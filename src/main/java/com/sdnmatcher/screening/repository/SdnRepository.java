package com.sdnmatcher.screening.repository;

import com.sdnmatcher.screening.config.ScreeningProperties;
import com.sdnmatcher.screening.model.SdnEntry;
import com.sdnmatcher.screening.service.NameNormalizer;
import org.springframework.stereotype.Repository;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.xml.sax.SAXException;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Repository
public class SdnRepository {
    private static final DateTimeFormatter OFAC_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);
    private static final Pattern FOUR_DIGIT_YEAR = Pattern.compile("(?<!\\d)(\\d{4})(?!\\d)");
    private final ScreeningProperties properties;
    private volatile Cache cache;

    public SdnRepository(ScreeningProperties properties) {
        this.properties = properties;
    }

    public List<SdnEntry> findAll() {
        Path path = Path.of(properties.sdnPath());
        if (!Files.exists(path)) {
            throw new IllegalStateException("SDN XML not found at " + path.toAbsolutePath());
        }
        try {
            FileSignature signature = new FileSignature(Files.getLastModifiedTime(path).toMillis(), Files.size(path));
            Cache current = cache;
            if (current != null && current.signature().equals(signature)) {
                return current.entries();
            }
            synchronized (this) {
                current = cache;
                if (current != null && current.signature().equals(signature)) {
                    return current.entries();
                }
                List<SdnEntry> entries = parse(path);
                cache = new Cache(signature, entries);
                return entries;
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Unable to read SDN XML metadata", exception);
        }
    }

    private List<SdnEntry> parse(Path path) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            Document document = factory.newDocumentBuilder().parse(path.toFile());
            NodeList nodes = document.getElementsByTagName("sdnEntry");
            List<SdnEntry> entries = new ArrayList<>(nodes.getLength());
            for (int i = 0; i < nodes.getLength(); i++) {
                entries.add(toEntry((Element) nodes.item(i)));
            }
            return List.copyOf(entries);
        } catch (ParserConfigurationException | SAXException | IOException exception) {
            throw new IllegalStateException("Unable to parse SDN XML", exception);
        }
    }

    private SdnEntry toEntry(Element element) {
        List<String> names = new ArrayList<>();
        names.add(join(directText(element, "firstName"), directText(element, "lastName")));
        addAliases(element, "aka", names);
        addAliases(element, "sdnAlias", names);
        List<LocalDate> dates = new ArrayList<>();
        List<Integer> years = new ArrayList<>();
        NodeList dobItems = element.getElementsByTagName("dateOfBirthItem");
        for (int i = 0; i < dobItems.getLength(); i++) {
            String value = text((Element) dobItems.item(i), "dateOfBirth");
            try {
                LocalDate date = LocalDate.parse(value, OFAC_DATE);
                dates.add(date);
                years.add(date.getYear());
            } catch (DateTimeParseException ignored) {
                extractSingleYear(value).ifPresent(years::add);
            }
        }
        return new SdnEntry(directText(element, "uid"), names.stream()
                .filter(name -> !name.isBlank())
                .map(NameNormalizer::normalize)
                .distinct()
                .toList(),
                dates.stream().distinct().toList(), years.stream().distinct().toList());
    }

    private static java.util.Optional<Integer> extractSingleYear(String value) {
        Matcher matcher = FOUR_DIGIT_YEAR.matcher(value);
        if (!matcher.find()) {
            return java.util.Optional.empty();
        }
        int year = Integer.parseInt(matcher.group(1));
        return matcher.find() ? java.util.Optional.empty() : java.util.Optional.of(year);
    }

    private static void addAliases(Element entry, String tagName, List<String> names) {
        NodeList aliases = entry.getElementsByTagName(tagName);
        for (int i = 0; i < aliases.getLength(); i++) {
            Element alias = (Element) aliases.item(i);
            names.add(join(text(alias, "firstName"), text(alias, "lastName")));
        }
    }

    private static String text(Element parent, String tag) {
        NodeList nodes = parent.getElementsByTagName(tag);
        return nodes.getLength() == 0 ? "" : nodes.item(0).getTextContent().trim();
    }

    private static String directText(Element parent, String tag) {
        for (Node child = parent.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE && child.getNodeName().equals(tag)) {
                return child.getTextContent().trim();
            }
        }
        return "";
    }

    private static String join(String first, String last) {
        return (first + " " + last).trim().replaceAll("\\s+", " ");
    }


    private record FileSignature(long modifiedMillis, long size) {
    }

    private record Cache(FileSignature signature, List<SdnEntry> entries) {
    }
}
