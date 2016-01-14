package name.peterka.tinymediamanager.scraper.csfd;

import net.xeoh.plugins.base.annotations.PluginImplementation;
import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tinymediamanager.scraper.MediaCastMember;
import org.tinymediamanager.scraper.MediaGenres;
import org.tinymediamanager.scraper.MediaMetadata;
import org.tinymediamanager.scraper.MediaProviderInfo;
import org.tinymediamanager.scraper.MediaScrapeOptions;
import org.tinymediamanager.scraper.MediaSearchOptions;
import org.tinymediamanager.scraper.MediaSearchOptions.SearchParam;
import org.tinymediamanager.scraper.MediaSearchResult;
import org.tinymediamanager.scraper.MediaType;
import org.tinymediamanager.scraper.UnsupportedMediaTypeException;
import org.tinymediamanager.scraper.http.Url;
import org.tinymediamanager.scraper.mediaprovider.IMovieMetadataProvider;
import org.tinymediamanager.scraper.util.MetadataUtil;
import org.tinymediamanager.scraper.util.StrgUtils;

import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A meta data provider for the site csfd.cz
 *
 * @author Martin Peterka
 */
@PluginImplementation
public class CsfdMetadataProvider implements IMovieMetadataProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(CsfdMetadataProvider.class);

	private static MediaProviderInfo providerInfo = createMediaProviderInfo();

	public CsfdMetadataProvider() {
	}

	private static MediaProviderInfo createMediaProviderInfo() {
		MediaProviderInfo providerInfo = new MediaProviderInfo("csfd", "Česko-Slovenská filmová databáze (CSFD.cz)",
				"<html><h3>Česko-Slovenská filmová databáze</h3><br />Available languages: CZ</html>",
				CsfdMetadataProvider.class.getResource("/csfd_cz.png"));
		return providerInfo;
	}

	@Override
	public MediaProviderInfo getProviderInfo() {
		return providerInfo;
	}

	@Override
	public MediaMetadata getMetadata(MediaScrapeOptions options) throws Exception {
		LOGGER.debug("getMetadata() " + options.toString());

		if (options.getType() != MediaType.MOVIE) {
			throw new UnsupportedMediaTypeException(options.getType());
		}

		String detailUrl;

		String optionsId = options.getId(getProviderInfo().getId());
		if (StringUtils.isNotBlank(optionsId)) {
			detailUrl = "http://www.csfd.cz/film/" + optionsId;
			LOGGER.debug("detailUrl by id=" + optionsId + ": " + detailUrl);
		} else if (options.getResult() != null){
			detailUrl = options.getResult().getUrl();
			LOGGER.debug("detailUrl by url=" + detailUrl);
		} else {
			throw new Exception("We did not get any useful movie url");
		}

		MediaMetadata md = new MediaMetadata(providerInfo.getId());


		Url url = new Url(detailUrl);
		try (InputStream in = url.getInputStream()) {
			Document doc = Jsoup.parse(in, "UTF-8", "");

			// title year
			addTitleYear(md, doc);

			// Genre
			addGenre(md, doc);

			// rating
			addRating(md, doc);

			// plot
			addPlot(md, doc);

			// poster
			addPoster(md, doc);

			// creators
			addCreators(md, doc);

			MediaSearchResult mediaSearchResult = new MediaSearchResult(getProviderInfo().getId()) ;
			mediaSearchResult.setMetadata(md);
			options.setResult(mediaSearchResult);

		} catch (Exception e) {
			LOGGER.error("Error parsing " + detailUrl + ": " + e.getMessage(), e);
			throw e;
		}

		return md;
	}

	private void addPoster(MediaMetadata md, Document doc) {
		Element poster = doc.getElementById("poster").getElementsByTag("img").first();
		String src = poster.attr("src");
		src = ImageUtil.fixImageUrl(src);
		md.storeMetadata(MediaMetadata.POSTER_URL, src);
	}

	private void addTitleYear(MediaMetadata md, Document doc) {
		// title / year
		// 	<meta property="og:title" content="Planeta opic / Planet of the Apes (1968)">
		Elements ogTitle = doc.getElementsByAttributeValue("property", "og:title");
		if (!ogTitle.isEmpty()) {
			String ogTitleContent = ogTitle.first().attr("content");
			Pattern p = Pattern.compile("(.*) / (.*) \\(([0-9]{4})\\)");
			Matcher m = p.matcher(ogTitleContent);
			if (m.matches()) {
				md.storeMetadata(MediaMetadata.TITLE, StrgUtils.removeCommonSortableName(m.group(1)));
				md.storeMetadata(MediaMetadata.ORIGINAL_TITLE, StrgUtils.removeCommonSortableName(m.group(2)));
				md.storeMetadata(MediaMetadata.YEAR, m.group(3));
			} else {
				Element header = doc.getElementsByClass("header").first();
				md.storeMetadata(MediaMetadata.TITLE, ((TextNode) header.getElementsByTag("h1").first().childNodes().get(0)).text().trim());
				String origin = doc.getElementsByClass("origin").first().text();
				Pattern originPattern = Pattern.compile(".*, ([0-9]{4}).*");
				Matcher originMatcher = originPattern.matcher(origin);
				if (originMatcher.matches()) {
					md.storeMetadata(MediaMetadata.YEAR, originMatcher.group(1));
				}
			}
		}
	}

	private void addGenre(MediaMetadata md, Document doc) {
		Elements genre = doc.getElementsByClass("genre");
		for (Element g : genre) {
			List<MediaGenres> tmmGenres = GenresUtil.getTmmGenres(g.text());
			for (MediaGenres tmmGenre : tmmGenres) {
				md.addGenre(tmmGenre);
			}
		}
	}

	private void addRating(MediaMetadata md, Document doc) {
		Elements ratings = doc.getElementsByClass("average");
		if (!ratings.isEmpty()) {
			String r = ratings.text();
			if (!r.isEmpty()) {
				try {
					r = r.replaceAll("%", "");
					double rating = Double.parseDouble(r) / 10.0;
					md.storeMetadata(MediaMetadata.RATING, rating);
					String voteCount = doc.getElementsByAttributeValue("itemProp", "ratingCount").attr("content");
					md.storeMetadata(MediaMetadata.VOTE_COUNT, voteCount);
				} catch (Exception e) {
					LOGGER.debug("could not parse rating" + e.getMessage(), e);
				}
			}
		}
	}

	private void addPlot(MediaMetadata md, Document doc) {
		String plot = doc.getElementById("plots").getElementsByClass("content").first().getElementsByTag("div").first().text();
		md.storeMetadata(MediaMetadata.PLOT, plot);
	}

	private void addCreators(MediaMetadata md, Document doc) {
		Elements creators = doc.getElementsByClass("creators").first().getElementsByTag("h4");

		for (int i = 0; i < creators.size(); i++) {
			Element creator = creators.get(i);
			MediaCastMember.CastType castType = getCastType(creator);

			Elements persons = creator.parent().getElementsByTag("a");
			for (int j = 0; j < persons.size(); j++) {
				String person = persons.get(j).text();
				MediaCastMember cm = new MediaCastMember();
				cm.setName(person);
				cm.setType(castType);
				md.addCastMember(cm);
			}
		}
	}

	private MediaCastMember.CastType getCastType(Element creator) {
		MediaCastMember.CastType castType = MediaCastMember.CastType.OTHER;
		String creatorType = creator.text();
		if ("Režie:".equals(creatorType)) {
			castType = MediaCastMember.CastType.DIRECTOR;
		}
		if ("Předloha:".equals(creatorType)) {
			castType = MediaCastMember.CastType.WRITER;
		}
		if ("Hrají:".equals(creatorType)) {
			castType = MediaCastMember.CastType.ACTOR;
		}
		return castType;
	}

	@Override
	public List<MediaSearchResult> search(MediaSearchOptions options) throws Exception {
		LOGGER.debug("search() " + options.toString());

		if (options.getMediaType() != MediaType.MOVIE) {
			throw new UnsupportedMediaTypeException(options.getMediaType());
		}

		List<MediaSearchResult> resultList = new ArrayList<MediaSearchResult>();
		String searchString = "";
		String searchQuery = "";
		Elements movieLinks = null;
		String year = options.get(MediaSearchOptions.SearchParam.YEAR);


		// 2. search for search string
		if (StringUtils.isNotEmpty(options.get(SearchParam.QUERY))) {
			try {
				String query = options.get(MediaSearchOptions.SearchParam.QUERY);
				searchQuery = query;
				query = MetadataUtil.removeNonSearchCharacters(query);
				searchString = Constants.BASE_URL + "/hledat/?q=" + URLEncoder.encode(query, "UTF-8");
				LOGGER.debug("search for everything: " + query);

				Url url = new Url(searchString);
				InputStream in = url.getInputStream();
				Document doc = Jsoup.parse(in, "UTF-8", "");
				in.close();
				// only look for movie links
				movieLinks = doc.getElementsByClass("film");
				LOGGER.debug("found " + movieLinks.size() + " search results");
			} catch (Exception e) {
				LOGGER.error(String.format("failed to search for %s: %s", searchQuery, e.getMessage()), e);
			}
		}

		if (movieLinks == null || movieLinks.isEmpty()) {
			LOGGER.debug("nothing found :(");
			return resultList;
		}

		for (Element movieLink : movieLinks) {
			try {
				MediaSearchResult sr = new MediaSearchResult(providerInfo.getId());
				sr.setId(StrgUtils.substr(movieLink.toString(), "film\\/(\\d+).*")); // CSFD ID
				sr.setTitle(movieLink.text());
				LOGGER.debug(String.format("found movie '%s', id=%s", sr.getTitle(), sr.getId()));

				Elements firstDecription = movieLink.parent().parent().getElementsByTag("p");
				if (!firstDecription.isEmpty()) {
					String description = firstDecription.get(0).text();
					int yearIndex = description.lastIndexOf(",");
					String movieYear = description.substring(yearIndex + 2).trim();// skrip comma
					sr.setYear(movieYear);
				} else {
					// z Dalsi nalezene zaznamy
					String yearDesc = movieLink.parent().getElementsByClass("film-year").text().replaceAll("\\(|\\)", "");
					sr.setYear(yearDesc);
				}
				sr.setMediaType(MediaType.MOVIE);
				sr.setUrl(Constants.BASE_URL + "/" + movieLink.attr("href"));
				String poster = movieLink.parent().parent().parent().getElementsByClass("film-poster-small").get(0).attr("src");
				sr.setPosterUrl(ImageUtil.fixImageUrl(poster));

				// check if it has at least a title and url
				if (StringUtils.isBlank(sr.getTitle()) || StringUtils.isBlank(sr.getUrl())) {
					continue;
				}

				// compare score based on names
				float score = MetadataUtil.calculateScore(searchQuery, sr.getTitle());

				if (year != null && !year.isEmpty() && !year.equals("0") && !year.equals(sr.getYear())) {
					LOGGER.debug("parsed year does not match search result year - downgrading score by 0.01");
					score = score - 0.01f;
				}
				sr.setScore(score);

				resultList.add(sr);
			} catch (Exception e) {
				LOGGER.warn("error parsing movie result: " + e.getMessage(), e);
			}
		}
		return resultList;
	}

}
