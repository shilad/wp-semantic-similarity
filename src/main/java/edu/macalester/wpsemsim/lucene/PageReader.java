package edu.macalester.wpsemsim.lucene;

import org.apache.commons.compress.archivers.ArchiveException;
import org.apache.commons.compress.compressors.bzip2.BZip2CompressorInputStream;
import org.apache.commons.io.FilenameUtils;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.*;
import java.util.Iterator;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.GZIPInputStream;

import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;

public class PageReader implements Iterable<Page> {
    private static final Logger LOG = Logger.getLogger(PageReader.class.getName());
    private File path;


    public PageReader(File path) {
        this.path = path;
    }

    @Override
    public Iterator<Page> iterator() {
        try {
            return new DocIterator(path);
        } catch (Exception e) {
            e.printStackTrace();  //To change body of catch statement use File | Settings | File Templates.
        }
        return null;
    }


    public class DocIterator implements Iterator<Page> {

        private XMLStreamReader reader;
        private Page buffer = null;

        public DocIterator(File path) throws IOException, ArchiveException, XMLStreamException {
            InputStream input = new BufferedInputStream(new FileInputStream(path));
            if (FilenameUtils.getExtension(path.toString()).toLowerCase().startsWith("bz")) {
                input = new BZip2CompressorInputStream(input);
            } else if (FilenameUtils.getExtension(path.toString()).equalsIgnoreCase("gz")) {
                input = new GZIPInputStream(input);
            }

            // get the default factory instance
            XMLInputFactory factory = XMLInputFactory.newInstance();

            // configure it to create readers that coalesce adjacent character sections
            // Setting this would be nice, but it results in a stackoverflow exception.  Grr!
    //         factory.setProperty(XMLInputFactory.IS_COALESCING, Boolean.TRUE);

            factory.setProperty(XMLInputFactory.IS_NAMESPACE_AWARE, Boolean.FALSE);
            this.reader = factory.createXMLStreamReader(input);
        }

        private void fillBuffer() {
            if (buffer != null) {
                return;
            }
            try {
                if (!searchElement("page", false)) {
                    return;
                }
                String title = matchTextElement("title", true);
                String ns = matchTextElement("ns", true);
                String id = matchTextElement("id", true);
                String redirect = null;
                if (matchElement("redirect", false, false)) {
                    redirect = reader.getAttributeValue(null, "title");
                }
                String text = searchTextElement("text", true);
                buffer =new Page(Integer.valueOf(ns), Integer.valueOf(id), redirect, title, text);
            } catch (XMLStreamException e) {
                LOG.severe("parsing page failed");
                e.printStackTrace();
            }
        }

        @Override
        public boolean hasNext() {
            fillBuffer();
            return (buffer != null);
        }

        public Page next() {
            fillBuffer();
            Page tmp = buffer;
            buffer = null;
            return tmp;
        }

        @Override
        public void remove() {
            throw new UnsupportedOperationException();
        }


        private boolean advance() throws XMLStreamException {
            if (reader.hasNext()) {
                reader.next();
                return true;
            } else {
                return false;
            }
        }

        /**
         * Gets the next opening element.
         * If it has the specified name, return true, and advance the cursor.
         * If eof is reached, or a different element is encountered return false, or
         * if failIfNotFound is set, through an exception.
         *
         * @param name
         * @param failIfNotFound
         * @return
         * @throws javax.xml.stream.XMLStreamException
         */
        private boolean matchElement(String name, boolean failIfNotFound) throws XMLStreamException {
            return matchElement(name, failIfNotFound, true);

        }
        private boolean matchElement(String name, boolean failIfNotFound, boolean advance) throws XMLStreamException {
            while (!reader.isStartElement() && reader.hasNext()) {
                reader.next();
            }
            if (!reader.hasNext() || reader.getEventType() == END_DOCUMENT) {
                if (failIfNotFound) {
                    throw new IllegalStateException("received eof while looking for " + name);
                } else {
                    return false;
                }
            }
            if (reader.getName().getLocalPart().equals(name)) {
                if (advance) advance();
                return true;
            } else if (failIfNotFound) {
                throw new IllegalStateException("found " + reader.getName() + " while looking for " + name);
            } else {
                return false;
            }
        }

        /**
         * Search for the next element with the specified name.
         * @param name
         * @param failIfNotFound
         * @return
         * @throws javax.xml.stream.XMLStreamException
         */
        private boolean searchElement(String name, boolean failIfNotFound) throws XMLStreamException {
            return searchElement(name, failIfNotFound, true);
        }
        private boolean searchElement(String name, boolean failIfNotFound, boolean advance) throws XMLStreamException {
            while (reader.hasNext() && !matchElement(name, false, advance)) {
                // match against the next element
                advance();
            }
            if (!reader.hasNext() && failIfNotFound) {
                throw new IllegalStateException("received eof while looking for " + name);
            } else if (!reader.hasNext() && !failIfNotFound) {
                return false;
            }
            return true;
        }

        /**
         * Reads the xml stream until the occurence of a particular tag, then
         * returns the textual contents of the tag.
         * @param name
         * @param failIfNotFound if true, through an Exception if parsing fails (instead of returning null).
         * @return
         * @throws javax.xml.stream.XMLStreamException
         */
        private String matchTextElement(String name, boolean failIfNotFound) throws XMLStreamException {
            if (!matchElement(name, failIfNotFound)) {
                return null;
            }
            if (reader.isCharacters()) {
                //			String s = reader.getText();
                //                        System.out.println("text is " + s);
                //			advance();
                //			return s;
                return getCoalescedText();
            } else if (reader.isEndElement()) {
                return "";	// no text element.
            } else if (failIfNotFound) {
                throw new IllegalStateException("no characters found following " + name);
            } else {
                return "";
            }
        }

        /**
         * Reads the xml stream until the occurence of a particular tag, then
         * returns the textual contents of the tag.
         * @param name
         * @param failIfNotFound if true, through an Exception if parsing fails (instead of returning null).
         * @return
         * @throws javax.xml.stream.XMLStreamException
         */
        private String searchTextElement(String name, boolean failIfNotFound) throws XMLStreamException {
            if (!searchElement(name, failIfNotFound)) {
                return null;
            }
            if (reader.isCharacters()) {
                //			String s = reader.getText();
                //			advance();
                //			return s;
                return getCoalescedText();
            } else if (reader.isEndElement()) {
                return "";	// no text element.
            } else if (failIfNotFound) {
                throw new IllegalStateException("no characters found following " + name);
            } else {
                return "";
            }
        }

        private static final int MAX_LENGTH = 500000;

        private String getCoalescedText() throws XMLStreamException {
            boolean tooLong = false;
            StringBuilder sb = new StringBuilder();
            while (reader.isCharacters()) {
                String t = reader.getText();
                if (sb.length() < MAX_LENGTH) {
                    sb.append(t);
                } else if (!tooLong) {
                    tooLong = true;
                    LOG.log(Level.WARNING, "XML element text too long (length capped at {0}).", MAX_LENGTH);
                }
                advance();
            }
            return sb.toString();
        }
        private static final String BEGIN_COMMENT = "<!--";
        private static final String END_COMMENT = "-->";

        private String stripComments(String text, String timestamp) {
            StringBuilder stripped = new StringBuilder();
            int i = 0;
            while (true) {
                int j = text.indexOf(BEGIN_COMMENT, i);
                if (j < 0) {
                    stripped.append(text.substring(i));
                    break;
                }
                stripped.append(text.substring(i, j));
                int k = text.indexOf(END_COMMENT, j);
                // FIXME: handling comments and end_comments could be better
                if (k < 0) {
                    LOG.log(Level.WARNING, "No end comment found in {0} ({1}).", new Object[] {buffer.getTitle(), timestamp});
                    break;
                }
                i = k + END_COMMENT.length();
            }
            return stripped.toString();
        }
    }

    public static void main(String args[]) throws XMLStreamException, FileNotFoundException, ArchiveException {
        String path = "/Users/shilad/Documents/IntelliJ/NbrViz/Macademia/dat/wikipedia/enwiki-20120802-pages-articles1.xml-p000000010p000010000.bz2";
        int i = 0;
        for (Page d : new PageReader(new File(path))) {
            if (i++ % 1000 == 0) {
                System.err.println("read " + i + ": " + d.getTitle());
            }
        }
    }
}
