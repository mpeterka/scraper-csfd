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
import java.util.HashSet;
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

	/*
	 * <meta property="og:title" content="Bourne Vermaächtnis, Das (2012)" /> <meta property="og:type" content="movie" /> <meta property="og:url"
	 * content="http://www.ofdb.de/film/226745,Das-Bourne-Vermächtnis" /> <meta property="og:image" content="http://img.ofdb.de/film/226/226745.jpg" />
	 * <meta property="og:site_name" content="OFDb" /> <meta property="fb:app_id" content="198140443538429" /> <script
	 * src="http://www.ofdb.de/jscripts/vn/immer_oben.js" type="text/javascript"></script>
	 */
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

		// we can only work further if we got a search result on ofdb.de
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

			// creators
			addCreators(md, doc);

		} catch (Exception e) {
			LOGGER.error("Error parsing " + detailUrl);
			throw e;
		}

		return md;
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
				md.storeMetadata(MediaMetadata.TITLE, m.group(1));
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
		if (StringUtils.isNotEmpty(options.get(MediaSearchOptions.SearchParam.QUERY)) && (movieLinks == null || movieLinks.isEmpty())) {
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

		HashSet<String> foundResultUrls = new HashSet<>();
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
				sr.setPosterUrl(movieLink.parent().parent().parent().getElementsByClass("film-poster-small").get(0).attr("src"));

				// check if it has at least a title and url
				if (StringUtils.isBlank(sr.getTitle()) || StringUtils.isBlank(sr.getUrl())) {
					continue;
				}

				foundResultUrls.add(sr.getUrl());

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
		List<MediaTrailer> trailers = new ArrayList<MediaTrailer>();
		if (!MetadataUtil.isValidImdbId(options.getImdbId())) {
			LOGGER.debug("IMDB id not found");
			return trailers;
		}
	/*
	 * function getTrailerData(ci) { switch (ci) { case 'http://de.clip-1.filmtrailer.com/9507_31566_a_1.flv?log_var=72|491100001 -1|-' : return
     * '<b>Trailer 1</b><br><i>(small)</i><br><br>&raquo; 160px<br><br>Download:<br>&raquo; <a href=
     * "http://de.clip-1.filmtrailer.com/9507_31566_a_1.wmv?log_var=72|491100001-1|-" >wmv</a><br>'; case
     * 'http://de.clip-1.filmtrailer.com/9507_31566_a_2.flv?log_var=72|491100001 -1|-' : return '<b>Trailer 1</b><br><i>(medium)</i><br><br>&raquo;
     * 240px<br><br>Download:<br>&raquo; <a href= "http://de.clip-1.filmtrailer.com/9507_31566_a_2.wmv?log_var=72|491100001-1|-" >wmv</a><br>'; case
     * 'http://de.clip-1.filmtrailer.com/9507_31566_a_3.flv?log_var=72|491100001 -1|-' : return '<b>Trailer 1</b><br><i>(large)</i><br><br>&raquo;
     * 320px<br><br>Download:<br>&raquo; <a href= "http://de.clip-1.filmtrailer.com/9507_31566_a_3.wmv?log_var=72|491100001-1|-" >wmv</a><br>&raquo;
     * <a href= "http://de.clip-1.filmtrailer.com/9507_31566_a_3.mp4?log_var=72|491100001-1|-" >mp4</a><br>&raquo; <a href=
     * "http://de.clip-1.filmtrailer.com/9507_31566_a_3.webm?log_var=72|491100001-1|-" >webm</a><br>'; case
     * 'http://de.clip-1.filmtrailer.com/9507_31566_a_4.flv?log_var=72|491100001 -1|-' : return '<b>Trailer 1</b><br><i>(xlarge)</i><br><br>&raquo;
     * 400px<br><br>Download:<br>&raquo; <a href= "http://de.clip-1.filmtrailer.com/9507_31566_a_4.wmv?log_var=72|491100001-1|-" >wmv</a><br>&raquo;
     * <a href= "http://de.clip-1.filmtrailer.com/9507_31566_a_4.mp4?log_var=72|491100001-1|-" >mp4</a><br>&raquo; <a href=
     * "http://de.clip-1.filmtrailer.com/9507_31566_a_4.webm?log_var=72|491100001-1|-" >webm</a><br>'; case
     * 'http://de.clip-1.filmtrailer.com/9507_31566_a_5.flv?log_var=72|491100001 -1|-' : return '<b>Trailer 1</b><br><i>(xxlarge)</i><br><br>&raquo;
     * 640px<br><br>Download:<br>&raquo; <a href= "http://de.clip-1.filmtrailer.com/9507_31566_a_5.wmv?log_var=72|491100001-1|-" >wmv</a><br>&raquo;
     * <a href= "http://de.clip-1.filmtrailer.com/9507_31566_a_5.mp4?log_var=72|491100001-1|-" >mp4</a><br>&raquo; <a href=
     * "http://de.clip-1.filmtrailer.com/9507_31566_a_5.webm?log_var=72|491100001-1|-" >webm</a><br>'; case
     * 'http://de.clip-1.filmtrailer.com/9507_39003_a_1.flv?log_var=72|491100001 -1|-' : return '<b>Trailer 2</b><br><i>(small)</i><br><br>&raquo;
     * 160px<br><br>Download:<br>&raquo; <a href= "http://de.clip-1.filmtrailer.com/9507_39003_a_1.wmv?log_var=72|491100001-1|-" >wmv</a><br>'; case
     * 'http://de.clip-1.filmtrailer.com/9507_39003_a_2.flv?log_var=72|491100001 -1|-' : return '<b>Trailer 2</b><br><i>(medium)</i><br><br>&raquo;
     * 240px<br><br>Download:<br>&raquo; <a href= "http://de.clip-1.filmtrailer.com/9507_39003_a_2.wmv?log_var=72|491100001-1|-" >wmv</a><br>'; case
     * 'http://de.clip-1.filmtrailer.com/9507_39003_a_3.flv?log_var=72|491100001 -1|-' : return '<b>Trailer 2</b><br><i>(large)</i><br><br>&raquo;
     * 320px<br><br>Download:<br>&raquo; <a href= "http://de.clip-1.filmtrailer.com/9507_39003_a_3.wmv?log_var=72|491100001-1|-" >wmv</a><br>&raquo;
     * <a href= "http://de.clip-1.filmtrailer.com/9507_39003_a_3.mp4?log_var=72|491100001-1|-" >mp4</a><br>&raquo; <a href=
     * "http://de.clip-1.filmtrailer.com/9507_39003_a_3.webm?log_var=72|491100001-1|-" >webm</a><br>'; case
     * 'http://de.clip-1.filmtrailer.com/9507_39003_a_4.flv?log_var=72|491100001 -1|-' : return '<b>Trailer 2</b><br><i>(xlarge)</i><br><br>&raquo;
     * 400px<br><br>Download:<br>&raquo; <a href= "http://de.clip-1.filmtrailer.com/9507_39003_a_4.wmv?log_var=72|491100001-1|-" >wmv</a><br>&raquo;
     * <a href= "http://de.clip-1.filmtrailer.com/9507_39003_a_4.mp4?log_var=72|491100001-1|-" >mp4</a><br>&raquo; <a href=
     * "http://de.clip-1.filmtrailer.com/9507_39003_a_4.webm?log_var=72|491100001-1|-" >webm</a><br>'; case
     * 'http://de.clip-1.filmtrailer.com/9507_39003_a_5.flv?log_var=72|491100001 -1|-' : return '<b>Trailer 2</b><br><i>(xxlarge)</i><br><br>&raquo;
     * 640px<br><br>Download:<br>&raquo; <a href= "http://de.clip-1.filmtrailer.com/9507_39003_a_5.wmv?log_var=72|491100001-1|-" >wmv</a><br>&raquo;
     * <a href= "http://de.clip-1.filmtrailer.com/9507_39003_a_5.mp4?log_var=72|491100001-1|-" >mp4</a><br>&raquo; <a href=
     * "http://de.clip-1.filmtrailer.com/9507_39003_a_5.webm?log_var=72|491100001-1|-" >webm</a><br>'; } }
     */
		Url url = null;
		String searchString = BASE_URL + "/view.php?page=suchergebnis&Kat=IMDb&SText=" + options.getImdbId();
		try {
			// search with IMDB
			url = new Url(searchString);
			InputStream in = url.getInputStream();
			Document doc = Jsoup.parse(in, "UTF-8", "");
			in.close();
			Elements filme = doc.getElementsByAttributeValueMatching("href", "film\\/\\d+,");
			if (filme == null || filme.isEmpty()) {
				LOGGER.debug("found no search results");
				return trailers;
			}
			LOGGER.debug("found " + filme.size() + " search results"); // hopefully
			// only one

			LOGGER.debug("get (trailer) details page");
			url = new Url(BASE_URL + "/" + StrgUtils.substr(filme.first().toString(), "href=\\\"(.*?)\\\""));
			in = url.getInputStream();
			doc = Jsoup.parse(in, "UTF-8", "");
			in.close();

			// OLD STYLE
			// <b>Trailer 1</b><br><i>(xxlarge)</i><br><br>&raquo; 640px<br><br>Download:<br>&raquo; <a href=
			// "http://de.clip-1.filmtrailer.com/9507_31566_a_5.wmv?log_var=72|491100001-1|-" >wmv</a><br>&raquo; <a href=
			// "http://de.clip-1.filmtrailer.com/9507_31566_a_5.mp4?log_var=72|491100001-1|-" >mp4</a><br>&raquo; <a href=
			// "http://de.clip-1.filmtrailer.com/9507_31566_a_5.webm?log_var=72|491100001-1|-" >webm</a><br>
			Pattern regex = Pattern.compile("return '(.*?)';");
			Matcher m = regex.matcher(doc.toString());
			while (m.find()) {
				String s = m.group(1);
				String tname = StrgUtils.substr(s, "<b>(.*?)</b>");
				String tpix = StrgUtils.substr(s, "raquo; (.*?)x<br>");
				// String tqual = StrgUtils.substr(s, "<i>\\((.*?)\\)</i>");

				// url + format
				Pattern lr = Pattern.compile("<a href=\"(.*?)\">(.*?)</a>");
				Matcher lm = lr.matcher(s);
				while (lm.find()) {
					String turl = lm.group(1);
					// String tformat = lm.group(2);
					MediaTrailer trailer = new MediaTrailer();
					trailer.setName(tname);
					// trailer.setQuality(tpix + " (" + tformat + ")");
					trailer.setQuality(tpix);
					trailer.setProvider("filmtrailer");
					trailer.setUrl(turl);
					LOGGER.debug(trailer.toString());
					trailers.add(trailer);
				}
			}

			// NEW STYLE (additional!)
			// <div class="clips" id="clips2" style="display: none;">
			// <img src="images/flag_de.gif" align="left" vspace="3" width="18" height="12">&nbsp;
			// <img src="images/trailer_6.gif" align="top" vspace="1" width="16" height="16" alt="freigegeben ab 6 Jahren">&nbsp;
			// <i>Trailer 1:</i>
			// <a href="http://de.clip-1.filmtrailer.com/2845_6584_a_1.flv?log_var=67|491100001-1|-">&nbsp;small&nbsp;</a> &nbsp;
			// <a href="http://de.clip-1.filmtrailer.com/2845_6584_a_2.flv?log_var=67|491100001-1|-">&nbsp;medium&nbsp;</a> &nbsp;
			// <a href="http://de.clip-1.filmtrailer.com/2845_6584_a_3.flv?log_var=67|491100001-1|-">&nbsp;large&nbsp;</a> &nbsp;
			// <a href="http://de.clip-1.filmtrailer.com/2845_6584_a_4.flv?log_var=67|491100001-1|-">&nbsp;xlarge&nbsp;</a> &nbsp;
			// <a href="http://de.clip-1.filmtrailer.com/2845_6584_a_5.flv?log_var=67|491100001-1|-">&nbsp;xxlarge&nbsp;</a> &nbsp;
			// <br>
			// <img src="images/flag_de.gif" align="left" vspace="3" width="18" height="12">&nbsp;
			// <img src="images/trailer_6.gif" align="top" vspace="1" width="16" height="16" alt="freigegeben ab 6 Jahren">&nbsp;
			// <i>Trailer 2:</i>
			// <a href="http://de.clip-1.filmtrailer.com/2845_8244_a_1.flv?log_var=67|491100001-1|-">&nbsp;small&nbsp;</a> &nbsp;
			// <a href="http://de.clip-1.filmtrailer.com/2845_8244_a_2.flv?log_var=67|491100001-1|-">&nbsp;medium&nbsp;</a> &nbsp;
			// <a href="http://de.clip-1.filmtrailer.com/2845_8244_a_3.flv?log_var=67|491100001-1|-">&nbsp;large&nbsp;</a> &nbsp;
			// <a href="http://de.clip-1.filmtrailer.com/2845_8244_a_4.flv?log_var=67|491100001-1|-">&nbsp;xlarge&nbsp;</a> &nbsp;
			// <a href="http://de.clip-1.filmtrailer.com/2845_8244_a_5.flv?log_var=67|491100001-1|-">&nbsp;xxlarge&nbsp;</a> &nbsp;
			// <br>
			// <img src="images/flag_de.gif" align="left" vspace="3" width="18" height="12">&nbsp;
			// <img src="images/trailer_6.gif" align="top" vspace="1" width="16" height="16" alt="freigegeben ab 6 Jahren">&nbsp;
			// <i>Trailer 3:</i>
			// <a href="http://de.clip-1.filmtrailer.com/2845_14749_a_1.flv?log_var=67|491100001-1|-">&nbsp;small&nbsp;</a> &nbsp;
			// <a href="http://de.clip-1.filmtrailer.com/2845_14749_a_2.flv?log_var=67|491100001-1|-">&nbsp;medium&nbsp;</a> &nbsp;
			// <a href="http://de.clip-1.filmtrailer.com/2845_14749_a_3.flv?log_var=67|491100001-1|-">&nbsp;large&nbsp;</a> &nbsp;
			// <a href="http://de.clip-1.filmtrailer.com/2845_14749_a_4.flv?log_var=67|491100001-1|-">&nbsp;xlarge&nbsp;</a> &nbsp;
			// <a href="http://de.clip-1.filmtrailer.com/2845_14749_a_5.flv?log_var=67|491100001-1|-">&nbsp;xxlarge&nbsp;</a> &nbsp;
			// <br>
			// <br>
			// </div>

			// new style size
			// 1 = 160 x 90 = small
			// 2 = 240 x 136 = medium
			// 3 = 320 x 180 = large
			// 4 = 400 x 226 = xlarge
			// 5 = 640 x 360 = xxlarge

			;

			regex = Pattern.compile("<i>(.*?)</i>(.*?)<br>", Pattern.DOTALL); // get them as single trailer line
			m = regex.matcher(doc.getElementsByClass("clips").html());
			while (m.find()) {
				// LOGGER.info(doc.getElementsByClass("clips").html());
				// parse each line with 5 qualities
				String tname = m.group(1).trim();
				tname = tname.replaceFirst(":$", ""); // replace ending colon

				String urls = m.group(2);
				// url + format
				Pattern lr = Pattern.compile("<a href=\"(.*?)\">(.*?)</a>");
				Matcher lm = lr.matcher(urls);
				while (lm.find()) {
					String turl = lm.group(1);
					String tpix = "";
					String tformat = lm.group(2).replaceAll("&nbsp;", "").trim();
					switch (tformat) {
						case "small":
							tpix = "90p";
							break;

						case "medium":
							tpix = "136p";
							break;

						case "large":
							tpix = "180p";
							break;

						case "xlarge":
							tpix = "226p";
							break;

						case "xxlarge":
							tpix = "360p";
							break;

						default:
							break;
					}
					MediaTrailer trailer = new MediaTrailer();
					trailer.setName(tname);
					// trailer.setQuality(tpix + " (" + tformat + ")");
					trailer.setQuality(tpix);
					trailer.setProvider("filmtrailer");
					trailer.setUrl(turl);
					LOGGER.debug(trailer.toString());
					trailers.add(trailer);
				}
			}
		} catch (Exception e) {
			if (url != null) {
				LOGGER.error("Error parsing {}", url.toString());
			} else {
				LOGGER.error("Error parsing {}", searchString);
			}

			throw e;
		}
		return trailers;
	}

}
