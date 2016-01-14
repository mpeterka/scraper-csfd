package name.peterka.tinymediamanager.scraper.csfd;

/**
 *
 */
public class ImageUtil {
	static String fixImageUrl(String src) {
		if (!src.startsWith("http")) {
			src = "http:" + src;// fix spatne url
		}
		return src;
	}
}
