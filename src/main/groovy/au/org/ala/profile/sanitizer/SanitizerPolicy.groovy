package au.org.ala.profile.sanitizer

import com.google.common.base.Predicate
import org.owasp.html.HtmlPolicyBuilder
import org.owasp.html.PolicyFactory

import java.util.regex.Pattern

import static SanitizerPolicyConstants.DEFAULT
import static SanitizerPolicyConstants.SINGLE_LINE
import static org.owasp.html.Sanitizers.BLOCKS
import static org.owasp.html.Sanitizers.FORMATTING
import static org.owasp.html.Sanitizers.IMAGES
import static org.owasp.html.Sanitizers.LINKS
import static org.owasp.html.Sanitizers.STYLES
import static org.owasp.html.Sanitizers.TABLES

/**
 * Fairly liberal HTML sanitization policy
 */
class SanitizerPolicy {

    // Font element attributes borrowed from the OWASP Java HTML Sanitizer Ebay sample policy

    // The 16 colors defined by the HTML Spec (also used by the CSS Spec)
    private static final Pattern COLOR_NAME = Pattern.compile(
            "(?:aqua|black|blue|fuchsia|gray|grey|green|lime|maroon|navy|olive|purple"
                    + "|red|silver|teal|white|yellow)");

    // HTML/CSS Spec allows 3 or 6 digit hex to specify color
    private static final Pattern COLOR_CODE = Pattern.compile(
            "(?:#(?:[0-9a-fA-F]{3}(?:[0-9a-fA-F]{3})?))");

    private static final Pattern NUMBER = Pattern.compile(
            "[+-]?(?:(?:[0-9]+(?:\\.[0-9]*)?)|\\.[0-9]+)");

    private static final Predicate<String> COLOR_NAME_OR_COLOR_CODE = matchesEither(COLOR_NAME, COLOR_CODE);

    final static FONT_ATTRIBUTES = new HtmlPolicyBuilder().allowElements("font")
            .allowAttributes("color").matching(COLOR_NAME_OR_COLOR_CODE).onElements("font")
            // font faces can break PDF reports but the font face will be stripped prior to the pdf report being generated so allow it here
            .allowAttributes("face").matching(Pattern.compile("[\\w;, \\-]+")).onElements("font")
            .allowAttributes("size").matching(NUMBER).onElements("font")
            .toFactory()

    // Only allow specific css classes on certain elements, e.g. thumbnail and inline-attribute-image on img elements
    // TODO check for allowed css classes
    final static CSS = new HtmlPolicyBuilder().allowElements("img").allowAttributes("class").onElements("img").toFactory()

    final sanitizer = FORMATTING.and(STYLES).and(LINKS).and(BLOCKS).and(IMAGES).and(TABLES).and(FONT_ATTRIBUTES).and(CSS)

    final singleLine = FORMATTING

    final Map<String, PolicyFactory> policies = [
            (SINGLE_LINE): singleLine,
            (DEFAULT)    : sanitizer
    ]

    /**
     * Sanitizes some HTML according to the policy defined in this class.
     * @param html
     * @return
     */
    String sanitizeField(SanitizedHtml annotation, String html) {
        sanitize(html, policies[annotation.value()] ?: sanitizer)
    }

    static String sanitize(String html, PolicyFactory policyFactory) {
        return html ? policyFactory.sanitize(html) : html
    }

    String sanitize(String html) {
        sanitize(html, sanitizer)
    }

    private static Predicate<String> matchesEither(
            final Pattern a, final Pattern b) {
        return new Predicate<String>() {
            public boolean apply(String s) {
                return a.matcher(s).matches() || b.matcher(s).matches();
            }
        };
    }
}
