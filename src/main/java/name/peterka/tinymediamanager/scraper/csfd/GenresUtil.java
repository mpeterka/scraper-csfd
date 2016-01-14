package name.peterka.tinymediamanager.scraper.csfd;

import org.tinymediamanager.scraper.MediaGenres;

import java.util.LinkedList;
import java.util.List;

/**
 * Genres helper
 */
public class GenresUtil {
	/*
		 * Maps scraper Genres to internal TMM genres
		 */
	static List<MediaGenres> getTmmGenres(String genreLine) {
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

	private static MediaGenres translateGenre(String genre) {
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
}
