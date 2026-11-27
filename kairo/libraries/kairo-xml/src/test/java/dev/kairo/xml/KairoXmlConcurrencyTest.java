package dev.kairo.xml;

import dev.kairo.ir.IRDocument;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

final class KairoXmlConcurrencyTest {

    @Test
    void parsesConcurrentValidMalformedAndXxePayloadsSafelyAcrossThreads() throws Exception {
        KairoXmlParser parser = new KairoXmlParser();

        String validXml = """
                <kairo>
                  <style src="styles/app.kcss"/>
                  <Screen id="screen-{{idx}}">
                    <Column gap="12" padding="16">
                      <Text id="title" value="{{ heading }}"/>
                      <Input id="field" bind:value="inputText" placeholder="Digite"/>
                      <Button id="go" onClick="submit">Enviar</Button>
                    </Column>
                  </Screen>
                </kairo>
                """;

        String xxeXml = """
                <?xml version="1.0"?>
                <!DOCTYPE leak [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
                <Screen id="evil"><Text value="&xxe;"/></Screen>
                """;

        String brokenXml = "<Screen id=\"broken\"><Column><Text value=\"unclosed\"></Screen>";

        int threads = 8;
        int iterationsPerThread = 35;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<Void>> tasks = new ArrayList<>();
            for (int t = 0; t < threads; t++) {
                final int threadIdx = t;
                tasks.add(() -> {
                    for (int i = 0; i < iterationsPerThread; i++) {
                        IRDocument validDoc = parser.parseDocument("valid_" + threadIdx + "_" + i + ".xml", validXml);
                        assertThat(validDoc.diagnostics().hasErrors()).isFalse();
                        assertThat(validDoc.root()).isNotNull();
                        assertThat(validDoc.root().componentType()).isEqualTo("Screen");

                        IRDocument xxeDoc = parser.parseDocument("xxe_" + threadIdx + "_" + i + ".xml", xxeXml);
                        assertThat(xxeDoc.diagnostics().hasErrors()).isTrue();
                        assertThat(xxeDoc.diagnostics().all()).anyMatch(d -> "XML_SYNTAX".equals(d.code()));

                        IRDocument brokenDoc = parser.parseDocument("broken_" + threadIdx + "_" + i + ".xml", brokenXml);
                        assertThat(brokenDoc.diagnostics().hasErrors()).isTrue();
                    }
                    return null;
                });
            }

            List<Future<Void>> futures = pool.invokeAll(tasks);
            for (Future<Void> future : futures) {
                future.get(10, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }
    }
}
