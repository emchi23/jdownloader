package jd.controlling;

import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.datatransfer.UnsupportedFlavorException;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.List;
import java.util.StringTokenizer;

import jd.controlling.linkcollector.LinkCollectingJob;
import jd.controlling.linkcollector.LinkCollector;

import org.appwork.utils.Regex;
import org.appwork.utils.StringUtils;
import org.appwork.utils.logging.Log;
import org.appwork.utils.os.CrossSystem;

public class ClipboardMonitoring {

    private static ClipboardMonitoring INSTANCE            = new ClipboardMonitoring();
    private static DataFlavor          urlFlavor           = null;
    private static DataFlavor          uriListFlavor       = null;
    private volatile Thread            monitoringThread    = null;
    private Clipboard                  clipboard           = null;
    private volatile boolean           skipChangeDetection = false;

    public synchronized void startMonitoring() {
        if (isMonitoring()) return;
        monitoringThread = new Thread() {
            private String oldStringContent = null;
            private String oldListContent   = null;

            @Override
            public void run() {
                while (Thread.currentThread() == monitoringThread) {
                    try {
                        synchronized (this) {
                            this.wait(750);
                        }
                        if (Thread.currentThread() != monitoringThread) return;
                    } catch (InterruptedException e) {
                        return;
                    }
                    if (skipChangeDetection) {
                        continue;
                    }
                    try {
                        Transferable currentContent = clipboard.getContents(null);
                        if (currentContent == null) continue;
                        String handleThisRound = null;
                        try {
                            /* change detection for List/URI content */
                            String newListContent = getListTransferData(currentContent);
                            try {
                                if (changeDetector(oldListContent, newListContent)) {
                                    handleThisRound = newListContent;
                                } else if (noChangeDetector(oldListContent, newListContent)) {
                                    continue;
                                }
                            } finally {
                                oldListContent = newListContent;
                            }
                        } catch (final Throwable e) {
                        }
                        if (handleThisRound == null) {
                            /* change detection for String/HTML content */
                            String newStringContent = getStringTransferData(currentContent);
                            try {
                                if (changeDetector(oldStringContent, newStringContent)) {
                                    /*
                                     * we only use normal String Content to
                                     * detect a change
                                     */
                                    handleThisRound = newStringContent;
                                    try {
                                        /*
                                         * lets fetch fresh HTML Content if
                                         * available
                                         */
                                        String htmlContent = getHTMLTransferData(currentContent);
                                        if (htmlContent != null) {
                                            handleThisRound = handleThisRound + "\r\n" + htmlContent;
                                        }
                                    } catch (final Throwable e) {
                                    }
                                }
                            } finally {
                                oldStringContent = newStringContent;
                            }
                        }
                        if (!StringUtils.isEmpty(handleThisRound)) {
                            LinkCollector.getInstance().addCrawlerJob(new LinkCollectingJob(handleThisRound));
                        }
                    } catch (final Throwable e) {
                    }
                }
            }

        };
        monitoringThread.setName("ClipboardMonitor");
        monitoringThread.setDaemon(true);
        monitoringThread.start();
    }

    public String getCurrentContent() {
        Transferable currentContent = null;
        try {
            currentContent = clipboard.getContents(null);
        } catch (final Throwable e) {
            return "";
        }
        String stringContent = null;
        try {
            stringContent = getStringTransferData(currentContent);
        } catch (final Throwable e) {
        }
        String htmlContent = null;
        try {
            /* lets fetch fresh HTML Content if available */
            htmlContent = getHTMLTransferData(currentContent);
        } catch (final Throwable e) {
        }
        StringBuilder sb = new StringBuilder();
        if (stringContent != null) sb.append(stringContent);
        if (sb.length() > 0) sb.append("\r\n");
        if (htmlContent != null) sb.append(htmlContent);
        return sb.toString();
    }

    public synchronized void setCurrentContent(String string) {
        try {
            clipboard.setContents(new StringSelection(string), new ClipboardOwner() {

                public void lostOwnership(Clipboard clipboard, Transferable contents) {
                    skipChangeDetection = false;
                }
            });
            skipChangeDetection = true;
        } catch (final Throwable e) {
            skipChangeDetection = false;
        }
    }

    private boolean changeDetector(String oldS, String newS) {
        if (oldS == null && newS != null) return true;
        if (oldS != null && newS != null && !oldS.equalsIgnoreCase(newS)) return true;
        return false;
    }

    private boolean noChangeDetector(String oldS, String newS) {
        if (oldS != null && newS != null && oldS.equalsIgnoreCase(newS)) return true;
        return false;
    }

    public synchronized void stopMonitoring() {
        if (monitoringThread != null) monitoringThread.interrupt();
        monitoringThread = null;
    }

    public synchronized boolean isMonitoring() {
        return monitoringThread != null && monitoringThread.isAlive();
    }

    public ClipboardMonitoring() {
        clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
    }

    static {
        try {
            urlFlavor = new DataFlavor("application/x-java-url; class=java.net.URL");
        } catch (final Throwable e) {
            Log.L.info("urlFlavor not supported");
        }
        try {
            uriListFlavor = new DataFlavor("text/uri-list; class=java.lang.String");
        } catch (final Throwable e) {
            Log.L.info("uriListFlavor not supported");
        }
    }

    public static String getHTMLTransferData(final Transferable transferable) throws UnsupportedFlavorException, IOException {
        DataFlavor htmlFlavor = null;
        /*
         * for our workaround for
         * https://bugzilla.mozilla.org/show_bug.cgi?id=385421, it would be good
         * if we have utf8 charset
         */
        for (final DataFlavor flav : transferable.getTransferDataFlavors()) {
            if (flav.getMimeType().contains("html") && flav.getRepresentationClass().isAssignableFrom(byte[].class)) {
                /*
                 * we use first hit and search UTF-8
                 */
                if (htmlFlavor != null) {
                    htmlFlavor = flav;
                }
                final String charSet = new Regex(flav.toString(), "charset=(.*?)]").getMatch(0);
                if ("UTF-8".equalsIgnoreCase(charSet)) {
                    /* we found utf-8 encoding, so lets use that */
                    htmlFlavor = flav;
                    break;
                }
            }
        }
        if (htmlFlavor != null) {
            /* we found a flavor for html content, lets fetch its content */
            final String charSet = new Regex(htmlFlavor.toString(), "charset=(.*?)]").getMatch(0);
            byte[] htmlBytes = (byte[]) transferable.getTransferData(htmlFlavor);
            if (CrossSystem.isLinux()) {
                /*
                 * workaround for firefox bug https://bugzilla
                 * .mozilla.org/show_bug .cgi?id=385421
                 */
                /*
                 * write check to skip broken first bytes and discard 0 bytes if
                 * they are in intervalls
                 */
                int indexOriginal = 0;
                for (int i = 6; i < htmlBytes.length - 1; i++) {
                    if (htmlBytes[i] != 0) {
                        /* copy byte */
                        htmlBytes[indexOriginal++] = htmlBytes[i];
                    }
                }
                String ret = null;
                if (charSet != null) {
                    ret = new String(htmlBytes, 0, indexOriginal, charSet);
                } else {
                    ret = new String(htmlBytes, 0, indexOriginal, "UTF-8");
                }
                return ret;
            } else {
                /* no workaround needed */
                if (charSet != null) {
                    return new String(htmlBytes, charSet);
                } else {
                    return new String(htmlBytes);
                }
            }
        }
        return null;
    }

    public static boolean hasSupportedTransferData(final Transferable transferable) {
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            /*
             * string and html always come together, so no need to check for
             * html
             */
            return true;
        } else if (urlFlavor != null && transferable.isDataFlavorSupported(urlFlavor)) {
            return true;
        } else if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            return true;
        } else if (uriListFlavor != null && transferable.isDataFlavorSupported(uriListFlavor)) {
            return true;
        } else {
            return false;
        }
    }

    public static void processSupportedTransferData(final Transferable transferable) {
        try {
            String content = getListTransferData(transferable);
            if (StringUtils.isEmpty(content)) {
                /* no List flavor available, lets check for String flavor */
                content = getStringTransferData(transferable);
                if (!StringUtils.isEmpty(content)) {
                    /* String flavor available */
                    String htmlContent = getHTMLTransferData(transferable);
                    if (!StringUtils.isEmpty(htmlContent)) {
                        /* add available HTML flavor to String flavor */
                        content = content + "\r\n" + htmlContent;
                    }
                }
            }
            if (!StringUtils.isEmpty(content)) {
                LinkCollector.getInstance().addCrawlerJob(new LinkCollectingJob(content));
            }
        } catch (final Throwable e) {
        }
    }

    public static String getStringTransferData(final Transferable transferable) throws UnsupportedFlavorException, IOException {
        if (transferable.isDataFlavorSupported(DataFlavor.stringFlavor)) { return (String) transferable.getTransferData(DataFlavor.stringFlavor); }
        return null;
    }

    public static String getURLTransferData(final Transferable transferable) throws UnsupportedFlavorException, IOException {
        if (urlFlavor != null && transferable.isDataFlavorSupported(urlFlavor)) { return ((URL) transferable.getTransferData(urlFlavor)).toString(); }
        return null;
    }

    @SuppressWarnings("unchecked")
    public static String getListTransferData(final Transferable transferable) throws UnsupportedFlavorException, IOException, URISyntaxException {
        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
            final List<File> list = (List<File>) transferable.getTransferData(DataFlavor.javaFileListFlavor);
            final StringBuilder sb = new StringBuilder("");
            for (final File f : list) {
                if (sb.length() > 0) sb.append("\r\n");
                sb.append("file://" + f.getPath());
            }
            return sb.toString();
        } else if (uriListFlavor != null && transferable.isDataFlavorSupported(uriListFlavor)) {
            /* url-lists are defined by rfc 2483 as crlf-delimited */
            final StringTokenizer izer = new StringTokenizer((String) transferable.getTransferData(uriListFlavor), "\r\n");
            final StringBuilder sb = new StringBuilder("");
            while (izer.hasMoreTokens()) {
                if (sb.length() > 0) sb.append("\r\n");
                final URI fi = new URI(izer.nextToken());
                if (fi.getScheme() != null && (fi.getScheme().contains("http") || fi.getScheme().contains("ftp"))) {
                    sb.append(fi.toString());
                } else {
                    sb.append(fi.getPath());
                }
            }
            return sb.toString();
        }
        return null;
    }

    /**
     * @return the iNSTANCE
     */
    public static ClipboardMonitoring getINSTANCE() {
        return INSTANCE;
    }

    public static void main(String[] args) throws InterruptedException {
        new ClipboardMonitoring().startMonitoring();
        Thread.sleep(100000);
    }
}
