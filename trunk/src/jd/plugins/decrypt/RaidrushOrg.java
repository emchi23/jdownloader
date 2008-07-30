//    jDownloader - Downloadmanager
//    Copyright (C) 2008  JD-Team jdownloader@freenet.de
//
//    This program is free software: you can redistribute it and/or modify
//    it under the terms of the GNU General Public License as published by
//    the Free Software Foundation, either version 3 of the License, or
//    (at your option) any later version.
//
//    This program is distributed in the hope that it will be useful,
//    but WITHOUT ANY WARRANTY; without even the implied warranty of
//    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
//    GNU General Public License for more details.
//
//    You should have received a copy of the GNU General Public License
//    along with this program.  If not, see <http://www.gnu.org/licenses/>.

package jd.plugins.decrypt;

import java.io.File;
import java.util.ArrayList;
import java.util.Vector;
import java.util.regex.Pattern;

import jd.http.Browser;
import jd.parser.Regex;
import jd.plugins.DownloadLink;
import jd.plugins.FilePackage;
import jd.plugins.PluginForDecrypt;
import jd.utils.JDUtilities;

public class RaidrushOrg extends PluginForDecrypt {

    static private final String host = "save.raidrush.ws";

    private Pattern patternSupported = Pattern.compile("http://[\\w\\.]*?raidrush\\.org/ext/\\?fid\\=[a-zA-Z0-9]+", Pattern.CASE_INSENSITIVE);

    // private String version = "0.1";

    public RaidrushOrg() {
        super();
    }

    @Override
    public ArrayList<DownloadLink> decryptIt(String parameter) {
        ArrayList<DownloadLink> decryptedLinks = new ArrayList<DownloadLink>();
        try {
            Browser br = new Browser();
            String page = br.getPage(parameter);
            String title = new Regex(page, "<big><strong>(.*?)</strong></big>").getFirstMatch();
            String pass = new Regex(page, "<strong>Passwort\\:</strong> <small>(.*?)</small>").getFirstMatch();
            FilePackage fp = new FilePackage();
            Vector<String> passes = new Vector<String>();
            passes.add(pass);
            fp.setName(title);
            fp.setPassword(pass);

            String[][] matches = new Regex(page, "ddl\\(\\'(.*?)\\'\\,\\'([\\d]*?)\\'\\)").getMatches();
            progress.setRange(matches.length);
            for (String[] match : matches) {

                String page2 = br.getPage("http://raidrush.org/ext/exdl.php?go=" + match[0] + "&fid=" + match[1]);
                String link = new Regex(page2, "unescape\\(\"(.*?)\"\\)").getFirstMatch();

                link = JDUtilities.htmlDecode(link);
                link = new Regex(link, "\"0\"><frame src\\=\"(.*?)\" name\\=\"GO_SAVE\"").getFirstMatch();
                DownloadLink dl = createDownloadlink(link);
                dl.setSourcePluginPasswords(passes);
                dl.setFilePackage(fp);
                decryptedLinks.add(dl);

                progress.increase(1);
            }

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
        return decryptedLinks;
    }

    @Override
    public boolean doBotCheck(File file) {
        return false;
    }

    @Override
    public String getCoder() {
        return "JD-Team";
    }

    @Override
    public String getHost() {
        return host;
    }

    @Override
    public String getPluginName() {
        return host;
    }

    @Override
    public Pattern getSupportedLinks() {
        return patternSupported;
    }

    @Override
    public String getVersion() {
        String ret = new Regex("$Revision$", "\\$Revision: ([\\d]*?) \\$").getFirstMatch();
        return ret == null ? "0.0" : ret;
    }
}