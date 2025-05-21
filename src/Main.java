import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Main {

    private static final HttpClient client = HttpClient.newHttpClient();
    private static final Pattern hrefPattern = Pattern.compile("href\\s*=\\s*\"(https?://[^\"]+)\"", Pattern.CASE_INSENSITIVE);

    public static void main(String[] args) {
        String inputFile = "urls.txt";
        String outputFile = "resultados.txt";

        try {
            List<String> urls = readUrls(inputFile);

            Map<String, Integer> resultados = new ConcurrentHashMap<>();

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<Void>> futures = urls.stream()
                    .map(url -> executor.submit((Callable<Void>) () -> {
                        int count = contarUrlsInternas(url);
                        resultados.put(url, count);
                        return null;
                    }))
                    .toList();

                for (Future<Void> future : futures) {
                    future.get(); // Esperar que todos terminen
                }
            }

            guardarResultados(outputFile, resultados);

            System.out.println("Proceso completado. Resultados guardados en: " + outputFile);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static List<String> readUrls(String filePath) throws Exception {
        try (BufferedReader reader = new BufferedReader(new FileReader(filePath))) {
            return reader.lines().filter(line -> !line.isBlank()).toList();
        }
    }

    private static void guardarResultados(String filePath, Map<String, Integer> resultados) throws Exception {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(filePath))) {
            for (Map.Entry<String, Integer> entry : resultados.entrySet()) {
                writer.write(entry.getKey() + " -> " + entry.getValue() + " enlaces internos");
                writer.newLine();
            }
        }
    }

    private static int contarUrlsInternas(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .build();

            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

            String body = response.body();

            String dominio = URI.create(url).getHost();

            Matcher matcher = hrefPattern.matcher(body);
            int count = 0;

            while (matcher.find()) {
                String link = matcher.group(1);
                if (link.contains(dominio)) {
                    count++;
                }
            }

            System.out.println("Procesado: " + url + " → " + count + " internos");
            return count;

        } catch (Exception e) {
            System.err.println("Error procesando " + url + ": " + e.getMessage());
            return 0;
        }
    }
}