package org.openmrs.addonindex.scheduled;

import static org.hamcrest.beans.HasPropertyWithValue.hasProperty;
import static org.hamcrest.collection.IsIterableContainingInOrder.contains;
import static org.hamcrest.core.AllOf.allOf;
import static org.hamcrest.core.Is.is;
import static org.hamcrest.core.IsCollectionContaining.hasItem;
import static org.hamcrest.core.IsNull.nullValue;
import static org.junit.Assert.assertThat;
import static org.openmrs.addonindex.TestUtil.getFileAsString;

import org.junit.Test;
import org.openmrs.addonindex.domain.AddOnInfoAndVersions;
import org.openmrs.addonindex.domain.AddOnVersion;

public class FetchDetailsToIndexTest {
	
	@Test
	public void testParsingConfigXmlForLanguages() throws Exception {
		FetchDetailsToIndex task = new FetchDetailsToIndex(null, null);
		AddOnVersion version = new AddOnVersion();
		AddOnInfoAndVersions addOnInfoAndVersions = new AddOnInfoAndVersions();
		task.handleConfigXml(getFileAsString("config.withNoRequirements.xml"), version);
		assertThat(version.getRequireOpenmrsVersion(), nullValue());
		assertThat(version.getRequireModules(), nullValue());
		assertThat(version.getSupportedLanguages(), contains("en", "fr", "de"));
	}
	
	@Test
	public void testParsingConfigXmlForRequiredOpenmrsVersion() throws Exception {
		FetchDetailsToIndex task = new FetchDetailsToIndex(null, null);
		AddOnVersion version = new AddOnVersion();
		AddOnInfoAndVersions addOnInfoAndVersions = new AddOnInfoAndVersions();
		task.handleConfigXml(getFileAsString("config.withRequiredVersion.xml"), version);
		assertThat(version.getRequireOpenmrsVersion(), is("1.11.3, 1.10.2 - 1.10.*, 1.9.9 - 1.9.*"));
		assertThat(version.getRequireModules(), nullValue());
	}
	
	@Test
	public void testParsingConfigXmlForRequiredModuleVersion() throws Exception {
		FetchDetailsToIndex task = new FetchDetailsToIndex(null, null);
		AddOnVersion version = new AddOnVersion();
		AddOnInfoAndVersions addOnInfoAndVersions = new AddOnInfoAndVersions();
		task.handleConfigXml(getFileAsString("config.withRequiredModules.xml"), version);
		assertThat(version.getRequireOpenmrsVersion(), is("1.11.3, 1.10.2 - 1.10.*, 1.9.9 - 1.9.*"));
		assertThat(version.getRequireModules().size(), is(2));
		assertThat(version.getRequireModules(), hasItem(allOf(
				hasProperty("module", is("org.openmrs.module.reporting")),
				hasProperty("version", is("?"))
		)));
		assertThat(version.getRequireModules(), hasItem(allOf(
				hasProperty("module", is("org.openmrs.event")),
				hasProperty("version", is("?"))
		)));
	}

    @Test
	public void testParsingConfigXmlForSettingModulePackageAndId() throws Exception {
	    FetchDetailsToIndex task = new FetchDetailsToIndex(null, null);
	    AddOnVersion version = new AddOnVersion();
	    task.handleConfigXml(getFileAsString("config.withRelativePathDtd.xml"), version);
	    assertThat(version.getModulePackage(), is("org.openmrs.module.mdrtb"));
	    assertThat(version.getModuleId(), is("mdrtb"));
	}

    @Test
	public void testParsingWithDoctypeRelativePath() throws Exception {
		FetchDetailsToIndex task = new FetchDetailsToIndex(null, null);
		AddOnVersion version = new AddOnVersion();
		AddOnInfoAndVersions addOnInfoAndVersions = new AddOnInfoAndVersions();
		task.handleConfigXml(getFileAsString("config.withRelativePathDtd.xml"), version);
		// just test that we could parse at all
	}
	
	@Test
	public void testParsingWithDoctypeRelativePathOnTwoLines() throws Exception {
		FetchDetailsToIndex task = new FetchDetailsToIndex(null, null);
		AddOnVersion version = new AddOnVersion();
		task.handleConfigXml(getFileAsString("config.withRelativePathDtdOnTwoLines.xml"), version);
		// just test that we could parse at all
	}
	
	@Test
	public void testParsingWithCommentedDoctype() throws Exception {
		FetchDetailsToIndex task = new FetchDetailsToIndex(null, null);
		AddOnVersion version = new AddOnVersion();
		AddOnInfoAndVersions addOnInfoAndVersions = new AddOnInfoAndVersions();
		task.handleConfigXml(getFileAsString("config.withCommentedDoctype.xml"), version);
		// just test that we could parse at all
	}
}