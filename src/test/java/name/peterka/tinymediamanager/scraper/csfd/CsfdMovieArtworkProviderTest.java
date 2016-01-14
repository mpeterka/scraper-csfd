package name.peterka.tinymediamanager.scraper.csfd;

import org.junit.Before;
import org.junit.Test;
import org.tinymediamanager.scraper.MediaArtwork;
import org.tinymediamanager.scraper.MediaLanguages;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.MediaType;

import java.util.List;

import static org.junit.Assert.assertNotNull;

/**
 *
 */
public class CsfdMovieArtworkProviderTest {

	CsfdMovieArtworkProvider ma;

	@Before
	public void setUp() {
		ma = new CsfdMovieArtworkProvider();
	}


	@Test
	public void testMediaMalaCarodejnice() throws Exception {
		MediaScrapeOptions options;
		List<MediaArtwork> artworks;

		options = new MediaScrapeOptions(MediaType.MOVIE);
		options.setLanguage(MediaLanguages.cs);
		options.setId(ma.getProviderInfo().getId(), "147525");
		options.setArtworkType(MediaArtwork.MediaArtworkType.POSTER);

		artworks = ma.getArtwork(options);

		assertNotNull(artworks);

	}
}