package com.search.indexer.config;

import com.search.indexer.model.AmazonProduct;
import com.search.indexer.model.ProductDocument;
import com.search.indexer.processor.EmbeddingProcessor;
import com.search.indexer.processor.ProductTransformer;
import com.search.indexer.reader.HuggingFaceParquetReader;
import com.search.indexer.writer.ElasticsearchBulkWriter;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.parameters.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.infrastructure.item.support.CompositeItemProcessor;
import org.springframework.batch.infrastructure.support.transaction.ResourcelessTransactionManager;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

@Configuration
public class BatchConfig {

    private final HuggingFaceParquetReader parquetReader;
    private final ProductTransformer productTransformer;
    private final EmbeddingProcessor embeddingProcessor;
    private final ElasticsearchBulkWriter bulkWriter;

    @Value("${indexer.batch.chunk-size:500}")
    private int chunkSize;

    public BatchConfig(HuggingFaceParquetReader parquetReader,
                       ProductTransformer productTransformer,
                       EmbeddingProcessor embeddingProcessor,
                       ElasticsearchBulkWriter bulkWriter) {
        this.parquetReader = parquetReader;
        this.productTransformer = productTransformer;
        this.embeddingProcessor = embeddingProcessor;
        this.bulkWriter = bulkWriter;
    }

    @Bean
    public PlatformTransactionManager transactionManager() {
        return new ResourcelessTransactionManager();
    }

    @Bean
    public CompositeItemProcessor<AmazonProduct, ProductDocument> compositeProcessor() {
        CompositeItemProcessor<AmazonProduct, ProductDocument> processor = new CompositeItemProcessor<>();
        processor.setDelegates(List.of(productTransformer, embeddingProcessor));
        return processor;
    }

    @Bean
    public Step indexingStep(JobRepository jobRepository) {
        return new StepBuilder("indexingStep", jobRepository)
                .<AmazonProduct, ProductDocument>chunk(chunkSize, transactionManager())
                .reader(parquetReader)
                .processor(compositeProcessor())
                .writer(bulkWriter)
                .faultTolerant()
                .skipLimit(500)
                .skip(Exception.class)
                .noSkip(RuntimeException.class)
                .retryLimit(3)
                .retry(java.io.IOException.class)
                .listener(new StepProgressListener())
                .build();
    }

    @Bean
    public Job catalogIndexingJob(JobRepository jobRepository, Step indexingStep) {
        return new JobBuilder("catalogIndexingJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(indexingStep)
                .listener(new JobCompletionListener())
                .build();
    }
}
