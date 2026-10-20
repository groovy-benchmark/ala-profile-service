package au.org.ala.profile.sanitization

import au.org.ala.profile.sanitizer.SanitizerPolicy
import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class SanitizerPolicySpec extends Specification {

    SanitizerPolicy policy = new SanitizerPolicy()

    def "Policy should #name"() {
        expect:
        output == policy.sanitize(input)

        where:
        name << [
            "remove script tags",
            "remove click handlers",
            "allow font attributes"
        ]

        input << [
            '<div>hello<script>alert(1);</script></div>',
            '<div onclick="alert(1);">1</div>',
            '<p><font face="Comic Sans" size="12" color="#aaa">surprise</font></p>'
        ]

        output << [
            '<div>hello</div>',
            '<div>1</div>',
            '<p><font face="Comic Sans" size="12" color="#aaa">surprise</font></p>'
        ]

    }
}
