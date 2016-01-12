/*
 * Copyright 2012 - 2015 Manuel Laggner
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package name.peterka.tinymediamanager.scraper.csfd;

import org.junit.Before;
import org.junit.Test;
import org.tinymediamanager.scraper.MediaCastMember;
import org.tinymediamanager.scraper.MediaLanguages;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.MediaSearchOptions;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.MediaType;
import org.tinymediamanager.scraper.mediaprovider.IMovieMetadataProvider;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class CsfdMetadataProviderTest {

	IMovieMetadataProvider mp;

	@Before
	public void setUp() {
		mp = new CsfdMetadataProvider();
	}

	@Test
	public void testSearchKrtek() throws Exception {
		MediaSearchOptions options = new MediaSearchOptions(MediaType.MOVIE, MediaSearchOptions.SearchParam.QUERY, "Krtek a autíčko");
		options.set(MediaSearchOptions.SearchParam.LANGUAGE, "cs");
		List<MediaSearchResult> results = mp.search(options);
		assertNotNull("Result", results);
		assertEquals("Krtek a autíčko", results.get(0).getTitle());
		assertEquals("1963", results.get(0).getYear());
		// result count
		assertEquals("Result count", 50, results.size());
	}

	@Test
	public void testSearchPlaneta() throws Exception {
		MediaSearchOptions options = new MediaSearchOptions(MediaType.MOVIE, MediaSearchOptions.SearchParam.QUERY, "Planeta");
		options.set(MediaSearchOptions.SearchParam.LANGUAGE, "cs");
		List<MediaSearchResult> results = mp.search(options);
		assertNotNull("Result", results);
		assertEquals("Planeta Yó", results.get(0).getTitle());// opice ne
		assertEquals("2011", results.get(0).getYear());
		// result count
		assertEquals("Result count", 50, results.size());
	}


	@Test
	public void testScrapePlanetaOpic() throws Exception {
		MediaScrapeOptions options;
		MediaMetadata md;

		options = new MediaScrapeOptions(MediaType.MOVIE);
		options.setLanguage(MediaLanguages.cs);
		options.setId(mp.getProviderInfo().getId(), "19977");

		md = mp.getMetadata(options);

		assertThat(md.getStringValue(MediaMetadata.TITLE)).isEqualTo("Planeta opic");
		assertThat(md.getStringValue(MediaMetadata.ORIGINAL_TITLE)).isEqualTo("Planet of the Apes");
		assertThat(md.getStringValue(MediaMetadata.YEAR)).isEqualTo("1968");
		assertThat(md.getStringValue(MediaMetadata.PLOT)).startsWith(
				"Někde ve vesmíru přece musí být něco, co je lepší než člověk.");
		assertThat(md.getStringValue(MediaMetadata.TAGLINE)).isEmpty();
		assertThat(md.getDoubleValue(MediaMetadata.RATING)).isBetween(8.2, 8.9);

		assertThat(md.getCastMembers(MediaCastMember.CastType.ACTOR)).isNotNull();
		assertThat(md.getCastMembers(MediaCastMember.CastType.ACTOR).size()).isEqualTo(16);
		assertThat(md.getCastMembers(MediaCastMember.CastType.ACTOR).get(0).getName()).isEqualTo("Charlton Heston");
		assertThat(md.getCastMembers(MediaCastMember.CastType.DIRECTOR)).isNotNull();
		assertThat(md.getCastMembers(MediaCastMember.CastType.DIRECTOR).size()).isEqualTo(1);
	}

	@Test
	public void testScrapeMalaCarodejnice() throws Exception {
		MediaScrapeOptions options;
		MediaMetadata md;

		options = new MediaScrapeOptions(MediaType.MOVIE);
		options.setLanguage(MediaLanguages.cs);
		options.setId(mp.getProviderInfo().getId(), "147525");

		md = mp.getMetadata(options);

		assertThat(md.getStringValue(MediaMetadata.TITLE)).isEqualTo("Malá čarodějnice");
		assertThat(md.getStringValue(MediaMetadata.YEAR)).isEqualTo("1984");
	}
}
