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
import org.tinymediamanager.scraper.MediaTrailer;
import org.tinymediamanager.scraper.MediaType;
import org.tinymediamanager.scraper.UnsupportedMediaTypeException;
import org.tinymediamanager.scraper.http.Url;
import org.tinymediamanager.scraper.mediaprovider.IMovieMetadataProvider;
import org.tinymediamanager.scraper.mediaprovider.IMovieTrailerProvider;
import org.tinymediamanager.scraper.util.MetadataUtil;
import org.tinymediamanager.scraper.util.StrgUtils;

import java.io.InputStream;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A meta data provider for the site csfd.cz
 *
 * @author Martin Peterka
 */
@PluginImplementation
public class CsfdMetadataProvider implements IMovieMetadataProvider, IMovieTrailerProvider {
	private static final Logger LOGGER = LoggerFactory.getLogger(CsfdMetadataProvider.class);
	private static final String BASE_URL = "http://www.csfd.cz";

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

		String detailUrl = "";

		if (StringUtils.isNotBlank(options.getId(getProviderInfo().getId())) || options.getResult() != null) {
			if (StringUtils.isNotBlank(options.getId(getProviderInfo().getId()))) {
				detailUrl = "http://www.csfd.cz/film/" + options.getId(getProviderInfo().getId());
			} else {
				detailUrl = options.getResult().getUrl();
			}
		}

		// case b)
		if (options.getResult() == null && StringUtils.isNotBlank(options.getId(MediaMetadata.IMDB))) {
			MediaSearchOptions searchOptions = new MediaSearchOptions(MediaType.MOVIE);
			searchOptions.set(SearchParam.IMDBID, options.getId(MediaMetadata.IMDB));
			try {
				List<MediaSearchResult> results = search(searchOptions);
				if (results != null && !results.isEmpty()) {
					options.setResult(results.get(0));
					detailUrl = options.getResult().getUrl();
				}
			} catch (Exception e) {
				LOGGER.warn("failed IMDB search: " + e.getMessage());
			}
		}

		if (StringUtils.isBlank(detailUrl)) {
			throw new Exception("We did not get any useful movie url");
		}

		MediaMetadata md = new MediaMetadata(providerInfo.getId());

		String csfdId = StrgUtils.substr(detailUrl, "film/(\\d++)");

		Url url;
		try {
			LOGGER.trace("get details page " + csfdId);
			url = new Url(detailUrl);
			InputStream in = url.getInputStream();
			Document doc = Jsoup.parse(in, "UTF-8", "");
			in.close();

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

		} catch (Exception e) {
			LOGGER.error("Error parsing " + detailUrl);
			throw e;
		}

		return md;
	}

	private void addPoster(MediaMetadata md, Document doc) {
		Element poster = doc.getElementById("poster").getElementsByTag("img").first();
		String src = poster.attr("src");
		src = fixPosterUrl(src);
		md.storeMetadata(MediaMetadata.POSTER_URL, src);
	}

	private String fixPosterUrl(String src) {
		if (!src.startsWith("http")) {
			src = "http:" + src;// fix spatne url
		}
		return src;
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
			List<MediaGenres> tmmGenres = getTmmGenres(g.text());
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

	/*
	 * Maps scraper Genres to internal TMM genres
	 */
	private List<MediaGenres> getTmmGenres(String genreLine) {
		LinkedList<MediaGenres> result = new LinkedList<>();
		String[] genres = genreLine.split(" / ");
		for (String genre : genres) {
			MediaGenres tmmGenre = translateGenre(genre);
			if (tmmGenre == null) {
				tmmGenre = MediaGenres.getGenre(genre);
			}
			result.add(tmmGenre);
		}
		return result;
	}

	private MediaGenres translateGenre(String genre) {
		switch (genre) {
			case "Akční":
				return MediaGenres.ACTION;
			case "Animovaný":
				return MediaGenres.ANIMATION;
			case "Dobrodružný":
				return MediaGenres.ADVENTURE;
			case "Dokumentární":
				return MediaGenres.DOCUMENTARY;
			case "Drama":
				return MediaGenres.DRAMA;
			case "Erotický":
				return MediaGenres.EROTIC;
			case "Experimentální":
				return MediaGenres.EROTIC;
			case "Fantasy":
				return MediaGenres.FANTASY;
			case "Film-Noir":
				return MediaGenres.FILM_NOIR;
			case "Historický":
				return MediaGenres.HISTORY;
			case "Horor":
				return MediaGenres.HORROR;
			case "Hudební":
				return MediaGenres.MUSIC;
			case "IMAX":
			case "Katastrofický":
				return MediaGenres.DISASTER;
			case "Komedie":
				return MediaGenres.COMEDY;
			case "Krátkometrážní":
				return MediaGenres.SHORT;
			case "Krimi":
				return MediaGenres.CRIME;
			case "Loutkový":
			case "Muzikál":
				return MediaGenres.MUSICAL;
			case "Mysteriózní":
				return MediaGenres.MYSTERY;
			case "Podobenství":
			case "Poetický":
			case "Pohádka":
			case "Povídkový":
			case "Psychologický":
			case "Publicistický":
				return MediaGenres.NEWS;
			case "Reality-TV":
				return MediaGenres.REALITY_TV;
			case "Road movie":
				return MediaGenres.ROAD_MOVIE;
			case "Rodinný":
				return MediaGenres.FAMILY;
			case "Romantický":
				return MediaGenres.ROMANCE;
			case "Sci-Fi":
				return MediaGenres.SCIENCE_FICTION;
			case "Soutěžní":
				return MediaGenres.GAME_SHOW;
			case "Sportovní":
				return MediaGenres.SPORT;
			case "Talk-show":
				return MediaGenres.TALK_SHOW;
			case "Taneční":
			case "Telenovela":
				return MediaGenres.TV_MOVIE;
			case "Thriller":
				return MediaGenres.THRILLER;
			case "Válečný":
				return MediaGenres.WAR;
			case "Western":
				return MediaGenres.WESTERN;
			case "Životopisný":
		}
		return null;
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
				searchString = BASE_URL + "/hledat/?q=" + URLEncoder.encode(query, "UTF-8");
				LOGGER.debug("search for everything: " + query);

				Url url = new Url(searchString);
				InputStream in = url.getInputStream();
				Document doc = Jsoup.parse(in, "UTF-8", "");
				in.close();
				// only look for movie links
				movieLinks = doc.getElementsByClass("film");
				LOGGER.debug("found " + movieLinks.size() + " search results");
			} catch (Exception e) {
				LOGGER.error("failed to search for " + searchQuery + ": " + e.getMessage());
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
				LOGGER.debug("found movie " + sr.getTitle());

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
				sr.setUrl(BASE_URL + "/" + movieLink.attr("href"));
				String poster = movieLink.parent().parent().parent().getElementsByClass("film-poster-small").get(0).attr("src");
				sr.setPosterUrl(fixPosterUrl(poster));

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
		Collections.sort(resultList);
		Collections.reverse(resultList);

		return resultList;
	}

	@Override
	public List<MediaTrailer> getTrailers(MediaScrapeOptions options) throws Exception {
		LOGGER.debug("getTrailers() " + options.toString());
		return new ArrayList<>();
	}

}
