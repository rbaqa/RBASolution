package techrba.data;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads test data from an external CSV file on the classpath so that business
 * scenarios are decoupled from test code (data-driven testing).
 *
 * <p>Source: {@code src/test/resources/testdata/exchange-transactions.csv}.
 * A {@link #transactionData()} method exposes the rows in the shape TestNG's
 * {@code @DataProvider} expects (an {@code Object[][]}). Column order follows
 * the file header and is documented by {@code COLUMNS}.</p>
 */
public final class CsvDataProvider {

    private static final Logger LOG = LogManager.getLogger(CsvDataProvider.class);
    private static final String RESOURCE = "testdata/exchange-transactions.csv";

    /** Column order of the CSV (matching the header row). */
    public static final String[] COLUMNS = {
            "transaction", "description", "mode", "currency1Id",
            "currency1Code", "amount", "currency2Id", "currency2Code"
    };

    private CsvDataProvider() {
        // static utility
    }

    /**
     * Returns {@code Object[][]} suitable for a TestNG {@code @DataProvider}.
     * Each row is: transaction, description, mode, c1id, c1code, amount,
     * c2id, c2code (in that order -- see {@link #COLUMNS}).
     */
    public static Object[][] transactionData() {
        List<String[]> rows = readAll();
        Object[][] data = new Object[rows.size()][COLUMNS.length];
        for (int i = 0; i < rows.size(); i++) {
            data[i] = rows.get(i);
        }
        LOG.info("Loaded {} transaction data rows from {}", rows.size(), RESOURCE);
        return data;
    }

    private static List<String[]> readAll() {
        List<String[]> rows = new ArrayList<>();
        try (InputStream in = CsvDataProvider.class.getClassLoader().getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Test data file not found on classpath: " + RESOURCE);
            }
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(in, StandardCharsets.UTF_8))) {
                // Skip header row
                reader.readLine();
                String line;
                while ((line = reader.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty()) {
                        continue;
                    }
                    // csv fields are numeric/simple so splitting on comma is safe here
                    rows.add(line.split(",", -1));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("Failed to read " + RESOURCE, e);
        }
        return rows;
    }
}
