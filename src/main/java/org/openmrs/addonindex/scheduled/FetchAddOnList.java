/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */

package org.openmrs.addonindex.scheduled;

import java.nio.charset.Charset;

import org.openmrs.addonindex.domain.AllAddOnsToIndex;
import org.openmrs.addonindex.service.IndexingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Fetches the list of Add-Ons that we need to index
 */
@Component
public class FetchAddOnList {
	
	private final Logger logger = LoggerFactory.getLogger(getClass());
	
	@Value("${add_on_list.url}")
	private String url;
	
	@Value("${add_on_list.strategy}")
	private Strategy strategy = Strategy.FETCH;
	
	private RestTemplateBuilder restTemplateBuilder;
	
	private ObjectMapper mapper;
	
	private IndexingService indexingService;
	
	@Autowired
	public FetchAddOnList(RestTemplateBuilder restTemplateBuilder,
	                      ObjectMapper mapper,
	                      IndexingService indexingService) {
		this.restTemplateBuilder = restTemplateBuilder;
		this.mapper = mapper;
		this.indexingService = indexingService;
	}
	
	@Scheduled(
			initialDelayString = "${scheduler.fetch_add_on_list.initial_delay}",
			fixedDelayString = "${scheduler.fetch_add_on_list.period}")
	public void fetchAddOnList() throws Exception {
		logger.info("Fetching list of add-ons to index");
		
		String json;
		if (strategy == Strategy.LOCAL) {
			logger.debug("LOCAL strategy");
			json = StreamUtils.copyToString(getClass().getClassLoader().getResourceAsStream("add-ons-to-index.json"),
					Charset.defaultCharset());
		} else {
			logger.debug("FETCH strategy: " + url);
			json = restTemplateBuilder.build().getForObject(url, String.class);
		}
		AllAddOnsToIndex toIndex;
		try {
			toIndex = mapper.readValue(json, AllAddOnsToIndex.class);
		}
		catch (Exception ex) {
			throw new RuntimeException("File downloaded from " + url + " could not be parsed", ex);
		}
		if (logger.isInfoEnabled()) {
			logger.info("We have " + toIndex.getToIndex().size() + " add-ons to index");
		}
		if (toIndex.size() > 0) {
			indexingService.setAllToIndex(toIndex);
		} else {
			logger.warn("File downloaded from " + url + " does not list any add-ons to index. Keeping our current list");
		}
	}
	
	public enum Strategy {
		FETCH, LOCAL
	}
}
