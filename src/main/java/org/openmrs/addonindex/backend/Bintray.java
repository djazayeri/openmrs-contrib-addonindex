/*
 * This Source Code Form is subject to the terms of the Mozilla Public License,
 * v. 2.0. If a copy of the MPL was not distributed with this file, You can
 * obtain one at http://mozilla.org/MPL/2.0/. OpenMRS is also distributed under
 * the terms of the Healthcare Disclaimer located at http://openmrs.org/license.
 *
 * Copyright (C) OpenMRS Inc. OpenMRS is a registered trademark and the OpenMRS
 * graphic logo is a trademark of OpenMRS Inc.
 */

package org.openmrs.addonindex.backend;

import java.io.IOException;
import java.time.OffsetDateTime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.extern.slf4j.Slf4j;
import org.openmrs.addonindex.domain.AddOnInfoAndVersions;
import org.openmrs.addonindex.domain.AddOnToIndex;
import org.openmrs.addonindex.domain.AddOnVersion;
import org.openmrs.addonindex.domain.BintrayGeoStats;
import org.openmrs.addonindex.domain.backend.BintrayPackageDetails;
import org.openmrs.addonindex.util.Version;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

@Component
@Slf4j
public class Bintray implements BackendHandler, SupportsDownloadCounts {

	private final RestTemplateBuilder restTemplateBuilder;

	private final ObjectMapper objectMapper;

	@Value("${bintray.username}")
	private String bintrayUsername;
	
	@Value("${bintray.api_key}")
	private String bintrayApiKey;
	
	@Autowired
	public Bintray(RestTemplateBuilder restTemplateBuilder, ObjectMapper objectMapper) {
		this.restTemplateBuilder = restTemplateBuilder;
		this.objectMapper = objectMapper;
	}
	
	@Override
	public AddOnInfoAndVersions getInfoAndVersionsFor(AddOnToIndex addOnToIndex) throws Exception {
		if (!StringUtils.hasText(bintrayUsername) || !StringUtils.hasText(bintrayApiKey)) {
			log.warn("You may need to specify the bintray.username and bintray.api_key configuration settings");
		}

		String url = packageUrlFor(addOnToIndex);
		ResponseEntity<String> entity = restTemplateBuilder.basicAuthentication(bintrayUsername, bintrayApiKey).build()
				.getForEntity(url, String.class);

		if (!entity.getStatusCode().is2xxSuccessful()) {
			log.warn("Problem fetching {} -> {} {}", url, entity.getStatusCode(), entity.getBody());
			return null;
		} else {
			String json = entity.getBody();
			return handlePackageJson(addOnToIndex, json);
		}
	}

	@Override
	public void fetchDownloadCounts(AddOnToIndex toIndex, AddOnInfoAndVersions infoAndVersions) {
		//We won't be checking for existing info as download counts is dynamic
		// and has to be fetched each time
		log.info("Fetching Download Counts for {}", toIndex.getUid());
		BintrayGeoStats downloadCounts;
		Integer totalDownloadCounts;
		String json;
		String downloadStatsUrl = downloadCountUrlFor(toIndex);
		try {
			json = restTemplateBuilder.build().getForObject(downloadStatsUrl, String.class);
			downloadCounts = objectMapper.readValue(json, BintrayGeoStats.class);
			totalDownloadCounts = handleBintrayGeoStats(toIndex, downloadCounts);
			infoAndVersions.setDownloadCountInLast30Days(totalDownloadCounts);
		} catch (Exception ex) {
			log.error("Error fetching details for {}", toIndex.getUid(), ex);
		}
	}

	AddOnInfoAndVersions handlePackageJson(AddOnToIndex addOnToIndex, String packageJson) throws IOException {
		AddOnInfoAndVersions info = AddOnInfoAndVersions.from(addOnToIndex);
		info.setHostedUrl(hostedUrlFor(addOnToIndex));
		ObjectNode obj = objectMapper.readValue(packageJson, ObjectNode.class);

		if (!StringUtils.hasText(info.getName())) {
			info.setName(obj.get("name").asText(""));
		}

		if (!StringUtils.hasText(info.getDescription())) {
			info.setDescription(obj.get("desc").asText(""));
		}

		String expectedFileExtension = "." + info.getType().getFileExtension();
		for (JsonNode node : obj.path("versions")) {
			String versionString = node.asText("");
			// TODO do we need to GET the version and make sure it's published?
			ArrayNode arr = restTemplateBuilder.basicAuthentication(bintrayUsername, bintrayApiKey).build()
					.getForObject(getVersionFilesUrlFor(addOnToIndex, versionString), ArrayNode.class);

			if (arr != null) {
				for (JsonNode fileNode : arr) {
					if (fileNode.get("name").asText("").endsWith(expectedFileExtension)) {
						// found the type of file we want, so assume this is the right file.
						// TODO maybe test that it has the version number in it?
						AddOnVersion version = new AddOnVersion();
						version.setVersion(new Version(versionString));
						version.setReleaseDatetime(OffsetDateTime.parse(fileNode.get("created").asText()));
						version.setDownloadUri(downloadUriFor(addOnToIndex, fileNode.get("path").asText()));
						info.addVersion(version);
						break;
					}

					log.debug("Skipping file: {}", arr.get("name").asText());
				}
			}
		}

		return info;
	}

	private Integer handleBintrayGeoStats(AddOnToIndex toIndex, BintrayGeoStats downloadCounts) {
		int totalDownloadCount = 0;
		for (Integer downloadCount : downloadCounts.getTotalDownloads().values()) {
			totalDownloadCount += downloadCount;
		}
		return totalDownloadCount;
	}
	
	private String downloadUriFor(AddOnToIndex addOnToIndex, String filePath) {
		BintrayPackageDetails details = addOnToIndex.getBintrayPackageDetails();
		return String.format("https://dl.bintray.com/%s/%s/%s",
				details.getOwner(),
				details.getRepo(),
				filePath);
	}
	
	private String getVersionFilesUrlFor(AddOnToIndex addOnToIndex, String versionString) {
		BintrayPackageDetails details = addOnToIndex.getBintrayPackageDetails();
		return String.format("https://bintray.com/api/v1/packages/%s/%s/%s/versions/%s/files?include_unpublished=0",
				details.getOwner(),
				details.getRepo(),
				details.getPackageName(),
				versionString);
	}
	
	private String hostedUrlFor(AddOnToIndex addOnToIndex) {
		BintrayPackageDetails details = addOnToIndex.getBintrayPackageDetails();
		return String.format("https://bintray.com/%s/%s/%s",
				details.getOwner(),
				details.getRepo(),
				details.getPackageName());
	}
	
	private String packageUrlFor(AddOnToIndex addOnToIndex) {
		BintrayPackageDetails details = addOnToIndex.getBintrayPackageDetails();
		return String.format("https://bintray.com/api/v1/packages/%s/%s/%s",
				details.getOwner(),
				details.getRepo(),
				details.getPackageName());
	}

	private String downloadCountUrlFor(AddOnToIndex addOnToIndex) {
		BintrayPackageDetails details = addOnToIndex.getBintrayPackageDetails();
		return String.format("https://bintray.com/statistics/packageGeoStats?&pkgPath=/%s/%s/%s",
				details.getOwner(),
				details.getRepo(),
				details.getPackageName());
	}
}
