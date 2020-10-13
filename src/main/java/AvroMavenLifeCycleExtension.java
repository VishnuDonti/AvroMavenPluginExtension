import org.apache.commons.io.FileUtils;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.codehaus.plexus.component.annotations.Component;
import org.json.JSONObject;
import org.json.XML;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component(role = AbstractMavenLifecycleParticipant.class)
public class AvroMavenLifeCycleExtension extends AbstractMavenLifecycleParticipant {

    public void afterProjectsRead(MavenSession session) throws MavenExecutionException {
        String avscFiles = System.getProperty("generateOnly");
        String considerSubModuleSpecific = System.getProperty("specificSubModuleClass");
        Optional<Plugin> extensions = session.getCurrentProject().getBuildPlugins().stream().filter(x -> x.isExtensions()).filter(x -> x.getArtifactId().equals("avro-maven-plugin")).findFirst();
        if (extensions.isPresent()) {
            String sourceDirectory = (String) session.getCurrentProject().getProperties().get("avro.modules");
            String finalDirectory = (String) session.getCurrentProject().getProperties().get("avro.sourceDirectory");
            String subModules = (String) session.getCurrentProject().getProperties().get("avro.subModules");
            Map<String, String> collect = FileUtils.listFiles(new File(subModules), new String[]{"avsc"}, true).stream().collect(
                    Collectors.toMap(subM -> subM.getName().replace(".avsc", ""), subM -> {
                        try {
                            return FileUtils.readFileToString(subM);
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                        return "";
                    })
            );
            FileUtils.listFiles(new File(sourceDirectory), new String[]{"avsc"}, true).forEach(x -> {
                try {
                    if (Objects.nonNull(avscFiles) && !avscFiles.contains(x.getName())) {
                        return;
                    }
                    if (x.getParent().contains(finalDirectory) || x.getParent().contains(subModules)) {
                        return;
                    }
                    List<String> lines = FileUtils.readLines(x, Charset.defaultCharset());
                    List<String> anImport = lines.stream().filter(x1 -> x1.contains("import"))
                            .collect(Collectors.toList());
                    lines.removeAll(anImport);

                    List<String> stringStream = lines.stream().map(x2 -> {
                        String replacedString = null;
                        for (Map.Entry<String, String> entry : collect.entrySet()) {
                            if (x2.contains(entry.getKey())) {
                                String value = entry.getValue();
                                if (Objects.nonNull(considerSubModuleSpecific) && considerSubModuleSpecific.equals("true")) {
                                    JSONObject jsonObjectMainModule = new JSONObject(lines.stream().collect(Collectors.joining()));
                                    String mainModuleName = jsonObjectMainModule.getString("name");
                                    JSONObject jsonObjectSubModule = new JSONObject(entry.getValue());
                                    jsonObjectSubModule.put("name", mainModuleName + jsonObjectSubModule.getString("name"));
                                    value = jsonObjectSubModule.toString();
                                }
                                replacedString = x2.replace("\"" + entry.getKey() + "\"", value);
                            }
                        }
                        if (Objects.nonNull(replacedString)) {
                            return replacedString;
                        }
                        return x2;
                    }).collect(Collectors.toList());
                    FileUtils.writeLines(new File(finalDirectory + x.getName()), stringStream);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            });
        }
    }
}
