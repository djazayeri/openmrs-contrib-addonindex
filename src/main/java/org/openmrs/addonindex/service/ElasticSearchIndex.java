/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */

package org.openmrs.addonindex.service;

import javax.annotation.PostConstruct;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.elasticsearch.ElasticsearchException;
import org.elasticsearch.action.search.SearchRequest;
import org.elasticsearch.action.search.SearchResponse;
import org.elasticsearch.client.RequestOptions;
import org.elasticsearch.client.Requests;
import org.elasticsearch.client.RestHighLevelClient;
import org.elasticsearch.client.core.CountRequest;
import org.elasticsearch.client.core.CountResponse;
import org.elasticsearch.client.indices.CreateIndexRequest;
import org.elasticsearch.client.indices.PutMappingRequest;
import org.elasticsearch.common.xcontent.XContentType;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.BoostingQueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.SearchHit;
import org.elasticsearch.search.builder.SearchSourceBuilder;
import org.elasticsearch.search.sort.SortOrder;
import org.openmrs.addonindex.domain.AddOnInfoAndVersions;
import org.openmrs.addonindex.domain.AddOnInfoSummary;
import org.openmrs.addonindex.domain.AddOnInfoSummaryAndStats;
import org.openmrs.addonindex.domain.AddOnType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Repository;
import org.springframework.util.StreamUtils;

@Repository
@Slf4j
public class ElasticSearchIndex implements Index {

	private static final int SEARCH_SIZE = 200;

	private static final int TOP_DOWNLOADS_SIZE = 20;

	private final RestHighLevelClient client;

	private final ObjectMapper objectMapper;

	@Autowired
	public ElasticSearchIndex(@Qualifier("restHighLevelClient") RestHighLevelClient client, ObjectMapper objectMapper) {
		this.client = client;
		this.objectMapper = objectMapper;
	}

	@PostConstruct
	public void setUp() throws IOException {
		try {
			CountResponse response = client.count(new CountRequest(AddOnInfoAndVersions.ES_INDEX), RequestOptions.DEFAULT);
			log.info("Existing ES index with {} documents", response.getCount());
		}
		catch (ElasticsearchException e) {
			if (e.status().getStatus() == 404) {
				// need to create the index
				log.info("Creating new ES index: {}", AddOnInfoAndVersions.ES_INDEX);
				client.indices().create(new CreateIndexRequest(AddOnInfoAndVersions.ES_INDEX), RequestOptions.DEFAULT);
			} else {
				throw e;
			}
		}

		log.info("Updating mappings on ES index");
		client.indices().putMapping(new PutMappingRequest(AddOnInfoAndVersions.ES_INDEX)
						.source(loadResource("elasticsearch/addOnInfoAndVersions-mappings.json"), XContentType.JSON),
				RequestOptions.DEFAULT);
	}

	@Override
	public void index(AddOnInfoAndVersions infoAndVersions) throws IOException {
		client.index(
				Requests.indexRequest(AddOnInfoAndVersions.ES_INDEX)
						.id(infoAndVersions.getUid())
						.source(objectMapper.writeValueAsString(infoAndVersions), XContentType.JSON),
				RequestOptions.DEFAULT);
	}

	@Override
	public Collection<AddOnInfoSummary> search(AddOnType type, String query, String tag) throws IOException {
		BoolQueryBuilder boolQB = QueryBuilders.boolQuery();
		if (type != null) {
			//Exact match on type
			boolQB.filter(QueryBuilders.matchQuery("type", type));
		}

		if (tag != null) {
			boolQB.filter(QueryBuilders.matchQuery("tags", tag));
		}

		if (query != null) {
			//Exact match on id(Highest priority)
			boolQB.should(QueryBuilders.termQuery("_id", query).boost(1700.0f));

			//Exact match on tag(High priority)
			boolQB.should(QueryBuilders.termQuery("tags", query).boost(1500.0f));

			//Prefix match of module name(Medium priority)
			boolQB.should(QueryBuilders.prefixQuery("name", query).boost(8.0f));

			//Query is subset of module name(Medium priority)
			boolQB.should(QueryBuilders.matchQuery("name", query).operator(MatchQueryBuilder.Operator.AND).boost(2.0f));

			//Description matches query either completely or partially(Low priority)
			boolQB.should(QueryBuilders.matchQuery("description", query).operator(MatchQueryBuilder.Operator.AND).boost(0.5f));

			//Allow for spelling mistake while searching for a particular module
			boolQB.should(QueryBuilders.fuzzyQuery("name", query));
			boolQB.minimumShouldMatch(1);
		}

		//Decrease ranking of those modules which are "Deprecated" or "Inactive"
		BoostingQueryBuilder boostingQB = QueryBuilders.boostingQuery(
				boolQB, QueryBuilders.termsQuery("status", "DEPRECATED", "INACTIVE"))
				.negativeBoost(0.001f);

		SearchResponse response = client.search(
				Requests.searchRequest(AddOnInfoAndVersions.ES_INDEX)
						.source(SearchSourceBuilder.searchSource().size(SEARCH_SIZE).query(boostingQB)), RequestOptions.DEFAULT);

		return Arrays.stream(response.getHits().getHits())
				.filter(SearchHit::hasSource)
				.map(hit -> objectMapper.convertValue(hit.getSourceAsMap(), AddOnInfoSummary.class))
				.collect(Collectors.toList());
	}

	@Override
	public Collection<AddOnInfoAndVersions> getAllByType(AddOnType type) throws IOException {
		SearchResponse response = client.search(new SearchRequest(AddOnInfoAndVersions.ES_INDEX)
						.source(new SearchSourceBuilder().size(SEARCH_SIZE).query(QueryBuilders.matchQuery("type", type))),
				RequestOptions.DEFAULT);

		return Arrays.stream(response.getHits().getHits())
				.filter(SearchHit::hasSource)
				.map(hit -> objectMapper.convertValue(hit.getSourceAsMap(), AddOnInfoAndVersions.class))
				.collect(Collectors.toList());
	}

	@Override
	public AddOnInfoAndVersions getByUid(String uid) throws IOException {
		return objectMapper.convertValue(
				client.get(Requests.getRequest(AddOnInfoAndVersions.ES_INDEX).id(uid), RequestOptions.DEFAULT).getSource(),
				AddOnInfoAndVersions.class);
	}

	@Override
	public AddOnInfoAndVersions getByModulePackage(String modulePackage) throws IOException {
		SearchResponse response = client.search(new SearchRequest(AddOnInfoAndVersions.ES_INDEX)
				.source(new SearchSourceBuilder()
						.query(QueryBuilders.matchQuery("modulePackage", modulePackage))
						.size(1)), RequestOptions.DEFAULT);

		if (response.getHits().getHits().length == 0) {
			return null;
		}

		return objectMapper.convertValue(response.getHits().getAt(0), AddOnInfoAndVersions.class);
	}

	@Override
	public Collection<AddOnInfoAndVersions> getByTag(String tag) throws IOException {
		SearchResponse response = client.search(new SearchRequest(AddOnInfoAndVersions.ES_INDEX)
						.source(new SearchSourceBuilder().size(SEARCH_SIZE).query(QueryBuilders.matchQuery("tags", tag))),
				RequestOptions.DEFAULT);

		return Arrays.stream(response.getHits().getHits())
				.filter(SearchHit::hasSource)
				.map(hit -> objectMapper.convertValue(hit.getSourceAsMap(), AddOnInfoAndVersions.class))
				.collect(Collectors.toList());
	}

	@Override
	public List<AddOnInfoSummaryAndStats> getTopDownloaded() throws IOException {
		SearchResponse response = client.search(Requests.searchRequest(AddOnInfoAndVersions.ES_INDEX)
				.source(SearchSourceBuilder.searchSource().size(TOP_DOWNLOADS_SIZE)
						.sort("downloadCountInLast30Days", SortOrder.DESC)
						.query(QueryBuilders.matchAllQuery())), RequestOptions.DEFAULT);

		return Arrays.stream(response.getHits().getHits())
				.filter(SearchHit::hasSource)
				.map(hit -> objectMapper.convertValue(hit.getSourceAsMap(), AddOnInfoAndVersions.class))
				.map(AddOnInfoSummaryAndStats::new)
				.collect(Collectors.toList());
	}

	private String loadResource(String name) throws IOException {
		InputStream inputStream = getClass().getClassLoader().getResourceAsStream(name);
		return StreamUtils.copyToString(inputStream, Charset.defaultCharset());
	}
}
