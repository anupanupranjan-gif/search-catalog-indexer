package com.search.indexer.reader;

import com.search.indexer.model.AmazonProduct;
import org.apache.avro.generic.GenericRecord;
import org.apache.parquet.avro.AvroParquetReader;
import org.apache.parquet.hadoop.ParquetReader;
import org.apache.parquet.io.InputFile;
import org.apache.parquet.io.SeekableInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.infrastructure.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.EOFException;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.RandomAccessFile;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class HuggingFaceParquetReader implements ItemReader<AmazonProduct> {

    private static final Logger log = LoggerFactory.getLogger(HuggingFaceParquetReader.class);

    private static final String DATASET_URL =
            "https://huggingface.co/datasets/milistu/AMAZON-Products-2023/resolve/refs%2Fconvert%2Fparquet/default/train/0000.parquet";

    @Value("${indexer.dataset.local-path:/tmp/amazon-products.parquet}")
    private String localParquetPath;

    @Value("${indexer.dataset.max-records:50000}")
    private int maxRecords;

    @Value("${indexer.dataset.skip-download:false}")
    private boolean skipDownload;

    private ParquetReader<GenericRecord> parquetReader;
    private final AtomicInteger recordCount  = new AtomicInteger(0);
    private final AtomicInteger skippedCount = new AtomicInteger(0);

    @PostConstruct
    public void init() throws Exception {
        File parquetFile = new File(localParquetPath);
        if (!parquetFile.exists() || !skipDownload) {
            downloadDataset(parquetFile);
        } else {
            log.info("Using existing dataset file at {}", localParquetPath);
        }
        openParquetReader(parquetFile);
        log.info("Reader initialized. Will index up to {} records", maxRecords);
    }

    @Override
    public AmazonProduct read() throws Exception {
        if (recordCount.get() >= maxRecords) {
            log.info("Reached max records limit: {}", maxRecords);
            return null;
        }

        GenericRecord record = parquetReader.read();
        if (record == null) {
            log.info("End of dataset. Total read: {}, Skipped: {}",
                    recordCount.get(), skippedCount.get());
            return null;
        }

        AmazonProduct product = mapToProduct(record);

        if (product.getTitle() == null || product.getTitle().isBlank()) {
            skippedCount.incrementAndGet();
            return read();
        }

        int count = recordCount.incrementAndGet();
        if (count % 5000 == 0) {
            log.info("Read {} records (skipped: {})", count, skippedCount.get());
        }

        return product;
    }

    private void downloadDataset(File targetFile) throws Exception {
        log.info("Downloading dataset from Hugging Face to {}", targetFile.getAbsolutePath());
        Files.createDirectories(targetFile.getParentFile().toPath());

        HttpClient client = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .build();

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(DATASET_URL))
                .header("User-Agent", "search-catalog-indexer/1.0")
                .GET()
                .build();

        HttpResponse<InputStream> response = client.send(
                request, HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() != 200) {
            throw new RuntimeException("Failed to download dataset. HTTP " + response.statusCode());
        }

        try (InputStream in = response.body();
             FileOutputStream out = new FileOutputStream(targetFile)) {
            byte[] buffer = new byte[8192];
            long totalBytes = 0;
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                out.write(buffer, 0, bytesRead);
                totalBytes += bytesRead;
                if (totalBytes % (10 * 1024 * 1024) == 0) {
                    log.info("Downloaded {} MB", totalBytes / 1024 / 1024);
                }
            }
            log.info("Download complete. {} MB", totalBytes / 1024 / 1024);
        }
    }

    private void openParquetReader(File parquetFile) throws IOException {
        // Pure Java NIO InputFile - no Hadoop, no Subject.getSubject()
        InputFile inputFile = new NioInputFile(parquetFile);
        parquetReader = AvroParquetReader.<GenericRecord>builder(inputFile).build();
        log.info("Parquet reader opened: {}", parquetFile.getAbsolutePath());
    }

    private AmazonProduct mapToProduct(GenericRecord record) {
        AmazonProduct product = new AmazonProduct();
        product.setParentAsin(getString(record, "parent_asin"));
        product.setTitle(getString(record, "title"));
        product.setDescription(record.get("description"));
        product.setMainCategory(getString(record, "main_category"));
        product.setStore(getString(record, "store"));
        product.setAverageRating(getDouble(record, "average_rating"));
        product.setRatingNumber(getInteger(record, "rating_number"));
        product.setImages(record.get("images"));
        product.setCategories(record.get("categories"));
        product.setFeatures(record.get("features"));

        Object priceObj = record.get("price");
        if (priceObj != null) {
            try {
                String priceStr = priceObj.toString().replace("$", "").replace(",", "").trim();
                if (!priceStr.isEmpty()) product.setPrice(Double.parseDouble(priceStr));
            } catch (NumberFormatException ignored) {}
        }
        return product;
    }

    private String getString(GenericRecord record, String field) {
        Object value = record.get(field);
        return value != null ? value.toString() : null;
    }

    private Double getDouble(GenericRecord record, String field) {
        Object value = record.get(field);
        if (value == null) return null;
        try { return Double.parseDouble(value.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    private Integer getInteger(GenericRecord record, String field) {
        Object value = record.get(field);
        if (value == null) return null;
        try { return Integer.parseInt(value.toString()); }
        catch (NumberFormatException e) { return null; }
    }

    @PreDestroy
    public void cleanup() {
        if (parquetReader != null) {
            try { parquetReader.close(); }
            catch (IOException e) { log.warn("Error closing reader", e); }
        }
    }

    // ── Pure Java NIO InputFile - zero Hadoop dependency at runtime ───────────

    static class NioInputFile implements InputFile {
        private final File file;

        NioInputFile(File file) { this.file = file; }

        @Override
        public long getLength() { return file.length(); }

        @Override
        public SeekableInputStream newStream() throws IOException {
            return new NioSeekableInputStream(new RandomAccessFile(file, "r"));
        }
    }

    static class NioSeekableInputStream extends SeekableInputStream {
        private final RandomAccessFile raf;

        NioSeekableInputStream(RandomAccessFile raf) { this.raf = raf; }

        @Override public long getPos() throws IOException { return raf.getFilePointer(); }
        @Override public void seek(long newPos) throws IOException { raf.seek(newPos); }

        @Override
        public void readFully(byte[] bytes) throws IOException {
            raf.readFully(bytes);
        }

        @Override
        public void readFully(byte[] bytes, int start, int len) throws IOException {
            raf.readFully(bytes, start, len);
        }

        @Override
        public int read(ByteBuffer buf) throws IOException {
            byte[] tmp = new byte[buf.remaining()];
            int n = raf.read(tmp);
            if (n > 0) buf.put(tmp, 0, n);
            return n;
        }

        @Override
        public void readFully(ByteBuffer buf) throws IOException {
            byte[] tmp = new byte[buf.remaining()];
            raf.readFully(tmp);
            buf.put(tmp);
        }

        @Override public int read() throws IOException { return raf.read(); }
        @Override public int read(byte[] b, int off, int len) throws IOException { return raf.read(b, off, len); }
        @Override public void close() throws IOException { raf.close(); }
    }
}
