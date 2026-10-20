package au.org.ala.profile.sanitizer;

/** Works around yet another groovy compiler bug */
public class SanitizerPolicyConstants {

    public static final String SINGLE_LINE = "singleLine";
    public static final String DEFAULT = "default";

    private SanitizerPolicyConstants() { throw new IllegalStateException(); }
}
