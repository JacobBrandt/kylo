/*
 * Copyright (c) 2016. Teradata Inc.
 */

package com.thinkbiganalytics.nifi.v2.ingest;


import com.thinkbiganalytics.ingest.TableMergeSyncSupport;
import com.thinkbiganalytics.nifi.v2.thrift.ThriftService;
import com.thinkbiganalytics.util.ComponentAttributes;
import com.thinkbiganalytics.util.PartitionBatch;
import com.thinkbiganalytics.util.PartitionSpec;

import org.apache.nifi.annotation.behavior.EventDriven;
import org.apache.nifi.annotation.behavior.InputRequirement;
import org.apache.nifi.annotation.documentation.CapabilityDescription;
import org.apache.nifi.annotation.documentation.Tags;
import org.apache.nifi.components.PropertyDescriptor;
import org.apache.nifi.flowfile.FlowFile;
import org.apache.nifi.logging.ProcessorLog;
import org.apache.nifi.processor.AbstractProcessor;
import org.apache.nifi.processor.ProcessContext;
import org.apache.nifi.processor.ProcessSession;
import org.apache.nifi.processor.Relationship;
import org.apache.nifi.processor.exception.ProcessException;
import org.apache.nifi.processor.util.StandardValidators;
import org.apache.nifi.util.StopWatch;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static com.thinkbiganalytics.nifi.v2.ingest.ComponentProperties.FEED_PARTITION;
import static com.thinkbiganalytics.nifi.v2.ingest.ComponentProperties.PARTITION_SPECIFICATION;
import static com.thinkbiganalytics.nifi.v2.ingest.ComponentProperties.REL_FAILURE;
import static com.thinkbiganalytics.nifi.v2.ingest.ComponentProperties.REL_SUCCESS;
import static com.thinkbiganalytics.nifi.v2.ingest.ComponentProperties.TARGET_TABLE;
import static com.thinkbiganalytics.nifi.v2.ingest.ComponentProperties.THRIFT_SERVICE;

@EventDriven
@InputRequirement(InputRequirement.Requirement.INPUT_ALLOWED)
@Tags({"hive", "ddl", "merge", "sync", "thinkbig"})
@CapabilityDescription("Fully synchronize or Merge values from a feed partition into the target table optionally supporting de-dupe and overwriting partitions. Sync will overwrite the entire table "
                       + "to match the source."
)
public class MergeTable extends AbstractProcessor {

    /**
     * Merge with dedupe
     **/
    public static final String STRATEGY_DEDUPE_MERGE = "DEDUPE_AND_MERGE";
    /**
     * Merge allowing duplicates
     **/
    public static final String STRATEGY_MERGE = "MERGE";

    /**
     * Sync replace everything in table
     **/
    public static final String STRATEGY_SYNC = "SYNC";

    private final Set<Relationship> relationships;


    public static final PropertyDescriptor SOURCE_TABLE = new PropertyDescriptor.Builder()
        .name("Source table")
        .description("Fully qualified name of the source table")
        .required(true)
        .defaultValue("${metadata.category.systemName}.${metadata.systemFeedName}_valid")
        .addValidator(StandardValidators.NON_EMPTY_VALIDATOR)
        .expressionLanguageSupported(true)
        .build();

    public static final PropertyDescriptor MERGE_STRATEGY = new PropertyDescriptor.Builder()
        .name("Merge Strategy")
        .description("Specifies the algorithm used to merge. Valid values are SYNC,MERGE,DEDUPE_AND_MERGE.  Sync will completely overwrite the target table with the source data. Merge will append "
                     + "the data into the target partitions. Dedupe will insert into the target partition but ensure no duplicate rows are remaining. ")
        .required(true)
        .expressionLanguageSupported(true)
        .allowableValues(STRATEGY_MERGE, STRATEGY_DEDUPE_MERGE, STRATEGY_SYNC, "${metadata.table.targetMergeStrategy}")
        .defaultValue("${metadata.table.targetMergeStrategy}")
        .build();

    private final List<PropertyDescriptor> propDescriptors;

    public MergeTable() {
        final Set<Relationship> r = new HashSet<>();
        r.add(REL_SUCCESS);
        r.add(REL_FAILURE);
        relationships = Collections.unmodifiableSet(r);

        final List<PropertyDescriptor> pds = new ArrayList<>();
        pds.add(THRIFT_SERVICE);
        pds.add(MERGE_STRATEGY);
        pds.add(SOURCE_TABLE);
        pds.add(TARGET_TABLE);
        pds.add(FEED_PARTITION);
        pds.add(PARTITION_SPECIFICATION);

        propDescriptors = Collections.unmodifiableList(pds);
    }

    @Override
    public Set<Relationship> getRelationships() {
        return relationships;
    }

    @Override
    protected List<PropertyDescriptor> getSupportedPropertyDescriptors() {
        return propDescriptors;
    }

    @Override
    public void onTrigger(final ProcessContext context, final ProcessSession session) throws ProcessException {
        final ProcessorLog logger = getLogger();
        FlowFile flowFile = session.get();
        if (flowFile == null) {
            return;
        }

        ThriftService thriftService = context.getProperty(THRIFT_SERVICE).asControllerService(ThriftService.class);
        String partitionSpecString = context.getProperty(PARTITION_SPECIFICATION).evaluateAttributeExpressions(flowFile).getValue();
        String sourceTable = context.getProperty(SOURCE_TABLE).evaluateAttributeExpressions(flowFile).getValue();
        String targetTable = context.getProperty(TARGET_TABLE).evaluateAttributeExpressions(flowFile).getValue();
        String feedPartitionValue = context.getProperty(FEED_PARTITION).evaluateAttributeExpressions(flowFile).getValue();
        String mergeStrategyValue = context.getProperty(MERGE_STRATEGY).evaluateAttributeExpressions(flowFile).getValue();

        logger.info("Using Source: " + sourceTable + " Target: " + targetTable + " feed partition:" + feedPartitionValue + " partSpec: " + partitionSpecString);

        final StopWatch stopWatch = new StopWatch(true);

        try (final Connection conn = thriftService.getConnection()) {

            TableMergeSyncSupport mergeSupport = new TableMergeSyncSupport(conn);
            PartitionSpec partitionSpec = new PartitionSpec(partitionSpecString);

            List<PartitionBatch> batches = null;

            if (STRATEGY_DEDUPE_MERGE.equals(mergeStrategyValue)) {
                mergeSupport.doMerge(sourceTable, targetTable, partitionSpec, feedPartitionValue, true);
            } else if (STRATEGY_MERGE.equals(mergeStrategyValue)) {
                mergeSupport.doMerge(sourceTable, targetTable, partitionSpec, feedPartitionValue, false);
            } else if (STRATEGY_SYNC.equals(mergeStrategyValue)) {
                mergeSupport.doSync(sourceTable, targetTable, partitionSpec, feedPartitionValue);
            } else {
                throw new UnsupportedOperationException("Failed to resolve the merge strategy");
            }

            // Record detail of each batch
            if (batches != null) {
                flowFile = session.putAttribute(flowFile, ComponentAttributes.NUM_MERGED_PARTITIONS.key(), String.valueOf(batches.size()));
                int i = 1;
                for (PartitionBatch batch : batches) {
                    flowFile = session.putAttribute(flowFile, ComponentAttributes.MERGED_PARTITION.key() + "." + i, batch.getBatchDescription());
                    flowFile = session.putAttribute(flowFile, ComponentAttributes.MERGED_PARTITION_ROWCOUNT.key() + "." + i, String.valueOf(batch.getRecordCount()));
                }
            }

            stopWatch.stop();
            session.getProvenanceReporter().modifyContent(flowFile, "Execution completed", stopWatch.getElapsed(TimeUnit.MILLISECONDS));
            session.transfer(flowFile, REL_SUCCESS);

        } catch (final Exception e) {
            logger.error("Unable to execute merge doMerge for {} due to {}; routing to failure", new Object[]{flowFile, e});
            session.transfer(flowFile, REL_FAILURE);
        }
    }
}