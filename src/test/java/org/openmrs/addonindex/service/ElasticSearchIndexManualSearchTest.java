package org.openmrs.addonindex.service;

import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.junit.Assert.assertThat;
import java.util.Collection;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openmrs.addonindex.domain.AddOnInfoAndVersions;
import org.openmrs.addonindex.domain.AddOnInfoSummary;
import org.openmrs.addonindex.domain.AddOnType;
import org.openmrs.addonindex.domain.AddOnVersion;
import org.openmrs.addonindex.util.Version;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.searchbox.client.JestClient;

/**
 * This test will write to your elasticsearch database
 */
@Ignore
@SpringBootTest
@RunWith(SpringRunner.class)
public class ElasticSearchIndexManualSearchTest {


    @Autowired
    private ElasticSearchIndex elasticSearchIndex;

    @Autowired
    private JestClient client;

    @Test
    public void testSearch() throws Exception {
        AddOnVersion v = new AddOnVersion();
        v.setVersion(new Version("1.0"));
        v.setDownloadUri("http://www.google.com");
        v.addLanguage("en");

        AddOnInfoAndVersions a = new AddOnInfoAndVersions();
        a.setUid("testing-module");
        a.setModulePackage("testing-mod");
        a.setModuleId("1");
        a.setType(AddOnType.OMOD);
        a.setName("Narrow Search Test 1");
        a.setDescription("A B C");
        a.addVersion(v);

        elasticSearchIndex.index(a);

        a = new AddOnInfoAndVersions();
        a.setUid("testing-module");
        a.setModulePackage("testing-mod");
        a.setModuleId("1");
        a.setType(AddOnType.OMOD);
        a.setName("Narrow Search Test 2");
        a.setDescription("A B");
        a.addVersion(v);

        elasticSearchIndex.index(a);

        //Test searching for description
        Collection<AddOnInfoSummary> result = elasticSearchIndex.search(AddOnType.OMOD, "B", null);
        assertThat(result.size(), greaterThanOrEqualTo(1));

        //Test narrowing description search
        Collection<AddOnInfoSummary> result2 = elasticSearchIndex.search(AddOnType.OMOD, "B C", null);
        assertThat(result.size(), greaterThan(result2.size()));

        //Test searching for name
        result = elasticSearchIndex.search(AddOnType.OMOD, "Test", null);
        assertThat(result.size(), greaterThanOrEqualTo(1));

        //Test narrowing name search
        result2 = elasticSearchIndex.search(AddOnType.OMOD, "Test 2", null);
        assertThat(result.size(), greaterThan(result2.size()));
    }

}
