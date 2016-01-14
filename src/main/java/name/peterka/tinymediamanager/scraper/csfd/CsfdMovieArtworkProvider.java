package name.peterka.tinymediamanager.scraper.csfd;

import net.xeoh.plugins.base.annotations.PluginImplementation;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.scraper.MediaArtwork;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.http.Url;
import org.tinymediamanager.scraper.mediaprovider.IMovieArtworkProvider;

import java.io.InputStream;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static name.peterka.tinymediamanager.scraper.csfd.Constants.BASE_URL;

/**
 * Artwork provider for csfd.cz
 */
@PluginImplementation
public class CsfdMovieArtworkProvider implements IMovieArtworkProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(CsfdMetadataProvider.class);


	private MediaProviderInfo providerInfo;

	public CsfdMovieArtworkProvider() {
		providerInfo = createMediaProviderInfo();
	}

	private static MediaProviderInfo createMediaProviderInfo() {
		MediaProviderInfo providerInfo = new MediaProviderInfo("csfd-artwork", "CSFD.cz galerie)",
				"<html><h3>Česko-Slovenská filmová databáze - stahovač galerie</h3><br />Available languages: CZ</html>",
				CsfdMetadataProvider.class.getResource("/csfd_cz.png"));
		return providerInfo;
	}

	@Override
	public MediaProviderInfo getProviderInfo() {
		return providerInfo;
	}

	/**
	 *
	 * @param options
	 * @return
	 * @throws Exception
	 */
	@Override
	public List<MediaArtwork> getArtwork(MediaScrapeOptions options) throws Exception {
		String csfdId = options.getId(getProviderInfo().getId());
		if (csfdId == null) {
			csfdId = options.getImdbId();//XXX je to tak, asi
		}

		LOGGER.debug("get artwork options " + options);
		LOGGER.debug("get artwork page id=" + csfdId);
		LinkedList<MediaArtwork> result = new LinkedList<>();

		Url url = new Url(BASE_URL + "/film/" + csfdId + "/galerie");
		InputStream in = url.getInputStream();
		Document doc = Jsoup.parse(in, "UTF-8", "");
		in.close();

		Elements photos = doc.getElementsByClass("photo");

		for (int i = 0; i < photos.size(); i++) {
			Element photo = photos.get(i);

			String style = photo.attr("style");
			Pattern p = Pattern.compile(".*'(.*)'.*");
			Matcher matcher = p.matcher(style);
			if (matcher.matches()) {
				String background = matcher.group(1);
				String backgroundUrl = ImageUtil.fixImageUrl(background);

				MediaArtwork artwork = new MediaArtwork();
				artwork.setType(MediaArtwork.MediaArtworkType.BACKGROUND);
				artwork.setDefaultUrl(backgroundUrl);

				LOGGER.debug("Found artwork at " + backgroundUrl);
				result.add(artwork);
			}

		}

		return result;
	}
}
