package com.cloudfuze.onboarding.email;

import org.springframework.stereotype.Component;

/**
 * The house style for every email the portal sends.
 *
 * <p>There used to be a copy of the shell in each composer, which is why they
 * had drifted into looking like a stock template: a saturated navy slab, a
 * rounded card on grey, and the message squeezed underneath. This is one
 * layout, and changing it changes every email at once.
 *
 * <h3>What it is going for</h3>
 * Quiet and deliberate rather than decorated. The Neutara lockup at a modest
 * size instead of a coloured banner, one clear headline, generous line height,
 * and a single thing to press. The restraint is the point - an offer letter arriving
 * from a company you are about to join should feel like correspondence, not
 * like marketing.
 *
 * <h3>Why it is built this way</h3>
 * <ul>
 *   <li><b>Tables, not divs.</b> Outlook renders through Word, which ignores
 *       max-width on a div and collapses the layout. A centred table is the
 *       only construction that holds up everywhere.</li>
 *   <li><b>No webfonts.</b> Gmail strips @font-face, so a brand face would show
 *       for some readers and not others - worse than one good stack for all.
 *       Georgia carries the headline because a serif reads as considered and is
 *       on every machine; the body is the system UI stack.</li>
 *   <li><b>A preheader.</b> The grey line the inbox shows beside the subject.
 *       Left unset it fills with whatever text comes first, which is usually
 *       "Hi Priya" - a wasted chance to say what the mail is.</li>
 * </ul>
 */
@Component
public class MailLayout {

    /* Deep navy carries the brand; the greys are warm rather than blue so the
       page reads as paper. Kept here as constants because an email cannot use
       CSS variables - every value has to be inlined at the point of use. */
    private static final String NAVY = "#174F96";
    private static final String INK = "#0f1b33";
    private static final String BODY = "#4a5568";
    private static final String MUTED = "#8b95a8";
    private static final String RULE = "#e6eaf2";
    private static final String CANVAS = "#f7f8fb";

    private static final String SANS =
            "-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Helvetica Neue',Arial,sans-serif";
    private static final String SERIF = "Georgia,'Times New Roman',serif";

    /**
     * The logo, attached to the message rather than linked.
     *
     * <p>A hotlinked image needs a publicly reachable URL, which this app does
     * not have yet, and it tells the sender's servers when a message is opened -
     * a tracking pixel by accident. An inline attachment travels with the mail,
     * works offline, and survives a reader with no network.
     *
     * <p>Most clients still block images until the reader allows them, so the
     * alt text is the wordmark in words. Blocked, the header reads "Neutara",
     * which is exactly what the image says.
     */
    public static final String LOGO_CID = "neutara-logo";

    /**
     * A complete email.
     *
     * @param preheader the line the inbox shows next to the subject
     * @param eyebrow   small label above the headline, e.g. "Offer letter"
     * @param heading   the one sentence this email exists to say
     * @param body      inner HTML, built from the helpers below
     */
    public String page(String preheader, String eyebrow, String heading, String body) {
        return """
                <div style="margin:0;padding:0;background:%s">
                  <div style="display:none;max-height:0;overflow:hidden;opacity:0;color:transparent;
                       height:0;width:0">%s</div>
                  <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                         style="background:%s;padding:40px 16px">
                    <tr>
                      <td align="center">
                        <table role="presentation" width="560" cellpadding="0" cellspacing="0" border="0"
                               style="width:560px;max-width:100%%;background:#ffffff;border:1px solid %s">
                          <tr>
                            <td style="padding:32px 40px 0">
                              <img src="cid:%s" alt="Neutara" width="104"
                                   style="display:block;width:104px;max-width:104px;height:auto;border:0;
                                   font-family:%s;font-size:13px;font-weight:700;letter-spacing:2px;
                                   text-transform:uppercase;color:%s" />
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:22px 40px 0">
                              <div style="height:1px;background:%s;line-height:1px;font-size:0">&nbsp;</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:26px 40px 38px">
                              %s
                              <h1 style="margin:0 0 18px;font-family:%s;font-size:26px;line-height:34px;
                                  font-weight:normal;color:%s">%s</h1>
                              %s
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:0 40px">
                              <div style="height:1px;background:%s;line-height:1px;font-size:0">&nbsp;</div>
                            </td>
                          </tr>
                          <tr>
                            <td style="padding:20px 40px 34px;font-family:%s;font-size:12px;
                                line-height:20px;color:%s">
                              Neutara &middot; People Operations<br />
                              This message and any link in it are personal to you. We will never ask for
                              your password or payment details.
                            </td>
                          </tr>
                        </table>
                      </td>
                    </tr>
                  </table>
                </div>
                """.formatted(CANVAS, escape(preheader), CANVAS, RULE, LOGO_CID, SANS, NAVY, RULE,
                eyebrow == null || eyebrow.isBlank() ? "" : eyebrow(eyebrow),
                SERIF, INK, escape(heading), body, RULE, SANS, MUTED);
    }

    private String eyebrow(String label) {
        return """
                <div style="font-family:%s;font-size:11px;font-weight:600;letter-spacing:1.6px;
                     text-transform:uppercase;color:%s;margin:0 0 12px">%s</div>
                """.formatted(SANS, MUTED, escape(label));
    }

    /** A paragraph of body copy. */
    public String p(String html) {
        return """
                <p style="margin:0 0 16px;font-family:%s;font-size:15px;line-height:26px;color:%s">%s</p>
                """.formatted(SANS, BODY, html);
    }

    /** The one thing to press. Only ever one per email. */
    public String button(String url, String label) {
        return """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0" style="margin:26px 0 8px">
                  <tr>
                    <td style="background:%s">
                      <a href="%s" style="display:inline-block;padding:14px 28px;font-family:%s;
                         font-size:15px;font-weight:600;color:#ffffff;text-decoration:none">%s</a>
                    </td>
                  </tr>
                </table>
                """.formatted(NAVY, url, SANS, escape(label));
    }

    /**
     * The same link as plain text under the button.
     *
     * <p>Buttons are images to some readers and stripped by others, and a
     * corporate mail gateway will happily rewrite one into something
     * unrecognisable. The URL in full is the fallback that always works.
     */
    public String fallbackLink(String url) {
        return """
                <p style="margin:0 0 4px;font-family:%s;font-size:12px;line-height:20px;color:%s">
                  Or paste this into your browser:<br />
                  <span style="color:%s;word-break:break-all">%s</span>
                </p>
                """.formatted(SANS, MUTED, NAVY, url);
    }

    /** Label-and-value rows, for the facts an email is reporting. */
    public String facts(String... labelsAndValues) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i + 1 < labelsAndValues.length; i += 2) {
            rows.append("""
                    <tr>
                      <td style="padding:7px 18px 7px 0;font-family:%s;font-size:13px;color:%s;
                          white-space:nowrap;vertical-align:top">%s</td>
                      <td style="padding:7px 0;font-family:%s;font-size:13px;color:%s;
                          vertical-align:top">%s</td>
                    </tr>
                    """.formatted(SANS, MUTED, escape(labelsAndValues[i]),
                    SANS, INK, escape(labelsAndValues[i + 1])));
        }
        return """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0"
                       style="margin:20px 0 8px;border-collapse:collapse">%s</table>
                """.formatted(rows);
    }

    /** A quiet callout - a list of what is needed, or a note worth setting apart. */
    public String panel(String html) {
        return """
                <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0"
                       style="margin:22px 0">
                  <tr>
                    <td style="border-left:3px solid %s;background:%s;padding:16px 18px;
                        font-family:%s;font-size:13.5px;line-height:23px;color:%s">%s</td>
                  </tr>
                </table>
                """.formatted(NAVY, CANVAS, SANS, BODY, html);
    }

    /** A numbered or bulleted list inside a panel or the body. */
    public String list(String... items) {
        StringBuilder rows = new StringBuilder();
        for (String item : items) {
            rows.append("""
                    <tr>
                      <td style="padding:0 10px 0 0;font-family:%s;font-size:13.5px;line-height:23px;
                          color:%s;vertical-align:top">&bull;</td>
                      <td style="padding:0 0 6px;font-family:%s;font-size:13.5px;line-height:23px;
                          color:%s">%s</td>
                    </tr>
                    """.formatted(SANS, NAVY, SANS, BODY, item));
        }
        return """
                <table role="presentation" cellpadding="0" cellspacing="0" border="0"
                       style="border-collapse:collapse">%s</table>
                """.formatted(rows);
    }

    /** Small print under the body - a deadline, or who to ask. */
    public String note(String html) {
        return """
                <p style="margin:18px 0 0;font-family:%s;font-size:12.5px;line-height:21px;color:%s">%s</p>
                """.formatted(SANS, MUTED, html);
    }

    public String escape(String value) {
        return value == null ? "" : value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    /** Wraps text in the body colour at normal weight, for use inside helpers. */
    public String strong(String value) {
        return "<strong style=\"font-weight:600;color:" + INK + "\">" + escape(value) + "</strong>";
    }
}
