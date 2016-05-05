create table CHANGE_SET (id binary(16) not null, created_time timestamp, modified_time timestamp, completeness_factor integer, intrinsic_period varchar(255), intrinsic_time timestamp, dataset_id binary(16), primary key (id));
create table CHANGE_SET_FILES (id binary(16) not null, primary key (id));
create table CHANGE_SET_FILES_PATH (change_set_files_id binary(16) not null, path varchar(255));
create table CHANGE_SET_HIVE_TABLE (record_count integer, id binary(16) not null, primary key (id));
create table CHANGE_SET_HIVE_TABLE_PART_VALUE (change_set_hive_table_id binary(16) not null, name varchar(255), value varchar(255));
create table DATA_OPERATION (id binary(16) not null, created_time timestamp, modified_time timestamp, start_time timestamp, state varchar(15), status varchar(2048), stop_time timestamp, dataset_id binary(16), producer_id binary(16), primary key (id));
create table DATASET (id binary(16) not null, created_time timestamp, modified_time timestamp, type varchar(10), datasource_id binary(16), primary key (id));
create table DATASOURCE (type varchar(31) not null, id binary(16) not null, created_time timestamp, modified_time timestamp, description varchar(255), name varchar(100), database_name varchar(255), table_name varchar(255), path varchar(255), primary key (id));
create table FEED (id binary(16) not null, created_time timestamp, modified_time timestamp, description varchar(255), display_name varchar(100), initialized char(1), name varchar(100) not null, state varchar(10) not null, sla_id binary(16), primary key (id));
create table FEED_DESTINATION (id binary(16) not null, created_time timestamp, modified_time timestamp, datasource_id binary(16), feed_id binary(16), primary key (id));
create table FEED_PROPERTIES (JpaFeed_id binary(16) not null, prop_value varchar(255), prop_key varchar(100) not null, primary key (JpaFeed_id, prop_key));
create table FEED_SOURCE (id binary(16) not null, created_time timestamp, modified_time timestamp, datasource_id binary(16), feed_id binary(16), agreement_id binary(16), primary key (id));
create table SLA (id binary(16) not null, created_time timestamp, modified_time timestamp, description varchar(255), name varchar(100), primary key (id));
create table SLA_METRIC (id binary not null, created_time timestamp, modified_time timestamp, metric varchar(255), obligation_id binary(16), primary key (id));
create table SLA_OBLIGATION (id binary(16) not null, created_time timestamp, modified_time timestamp, description varchar(255), group_id binary(16), primary key (id));
create table SLA_OBLIGATION_GROUP (id binary(16) not null, created_time timestamp, modified_time timestamp, cond varchar(10), agreement_id binary(16), primary key (id));
