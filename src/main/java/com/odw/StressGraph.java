package com.odw;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;

import com.google.common.io.ByteStreams;


public class StressGraph
{
	
    // PRINT FORMATTING
    public static final String HEADFORMAT = "%-10s%10s,%8s,%8s,%8s,%8s,%8s,%8s,%8s,%8s,%8s,%7s,%9s,%7s,%7s,%8s,%8s,%8s,%8s";
    public static final String ROWFORMAT =  "%-10s%10d,%8.0f,%8.0f,%8.0f,%8.1f,%8.1f,%8.1f,%8.1f,%8.1f,%8.1f,%7.1f,%9.5f,%7d,%7.0f,%8.0f,%8.0f,%8.0f,%8.0f";
    public static final String[] HEADMETRICS = new String[]{"type,", "total ops","op/s","pk/s","row/s","mean","med",".95",".99",".999","max","time","stderr", "errors", "gc: #", "max ms", "sum ms", "sdv ms", "mb"};
    public static final String HEAD = String.format(HEADFORMAT, (Object[]) HEADMETRICS);

    private String option;
    
    private String outputFile;
    
    private String title;
    
    private String revision;
    
    private String statsLogFile;
    
    
    private enum ReadingMode
    {
        START,
        METRICS,
        AGGREGATES,
        NEXTITERATION
    }
    private String stressArguments;

    /**
     * 
     * @param option
     * @param outputFile
     * @param title
     * @param stressArguments
     * @param revision
     * @param statsLogFile
     */
    public StressGraph(String option, String outputFile, String title, String stressArguments, String revision, String statsLogFile)
    {
        this.stressArguments = stressArguments;
        this.option = option;
        this.outputFile = outputFile;
        this.revision = revision;
        this.statsLogFile = statsLogFile;
        this.title = title;
    }

    public void generateGraph()
    {
        File htmlFile = new File(outputFile);
        JSONObject stats;
        if (htmlFile.isFile())
        {
            try
            {
                String html = new String(Files.readAllBytes(Paths.get(htmlFile.toURI())), StandardCharsets.UTF_8);
                stats = parseExistingStats(html);
            }
            catch (IOException e)
            {
                throw new RuntimeException("Couldn't load existing stats html.");
            }
            stats = this.createJSONStats(stats);
        }
        else
        {
            stats = this.createJSONStats(null);
        }

        try
        {
            PrintWriter out = new PrintWriter(htmlFile);
            String statsBlock = "/* stats start */\nstats = " + stats.toJSONString() + ";\n/* stats end */\n";
            String html = getGraphHTML().replaceFirst("/\\* stats start \\*/\n\n/\\* stats end \\*/\n", statsBlock);
            out.write(html);
            out.close();
        }
        catch (IOException e)
        {
            throw new RuntimeException("Couldn't write stats html.");
        }
    }

    private JSONObject parseExistingStats(String html)
    {
        JSONObject stats;

        Pattern pattern = Pattern.compile("(?s).*/\\* stats start \\*/\\nstats = (.*);\\n/\\* stats end \\*/.*");
        Matcher matcher = pattern.matcher(html);
        matcher.matches();
        stats = (JSONObject) JSONValue.parse(matcher.group(1));

        return stats;
    }

    private String getGraphHTML()
    {
        InputStream graphHTMLRes = this.
                getClass().getResourceAsStream("/graph.html");
        String graphHTML;
        try
        {
            graphHTML = new String(ByteStreams.toByteArray(graphHTMLRes));
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
        return graphHTML;
    }

    /** Parse log and append to stats array */
    private JSONArray parseLogStats(InputStream log, JSONArray stats) {
        BufferedReader reader = new BufferedReader(new InputStreamReader(log));
        JSONObject json = new JSONObject();
        JSONArray intervals = new JSONArray();
        boolean runningMultipleThreadCounts = false;
        String currentThreadCount = null;
        Pattern threadCountMessage = Pattern.compile("Running ([A-Z]+) with ([0-9]+) threads .*");
        ReadingMode mode = ReadingMode.START;

        try
        {
            String line;
            while ((line = reader.readLine()) != null)
            {
                // Detect if we are running multiple thread counts:
                if (line.startsWith("Thread count was not specified"))
                    runningMultipleThreadCounts = true;

                if (runningMultipleThreadCounts)
                {
                    // Detect thread count:
                    Matcher tc = threadCountMessage.matcher(line);
                    if (tc.matches())
                    {
                        currentThreadCount = tc.group(2);
                    }
                }
                
                // Detect mode changes
                if (line.equals(StressGraph.HEAD))
                {
                    mode = ReadingMode.METRICS;
                    continue;
                }
                else if (line.equals("Results:"))
                {
                    mode = ReadingMode.AGGREGATES;
                    continue;
                }
                else if (mode == ReadingMode.AGGREGATES && line.equals("END"))
                {
                    mode = ReadingMode.NEXTITERATION;
                }
                else if (line.equals("FAILURE"))
                {
                    break;
                }

                // Process lines
                if (mode == ReadingMode.METRICS)
                {
                    JSONArray metrics = new JSONArray();
                    String[] parts = line.split(",");
                    if (parts.length != StressGraph.HEADMETRICS.length)
                    {
                        continue;
                    }
                    for (String m : parts)
                    {
                        try
                        {
                            metrics.add(new BigDecimal(m.trim()));
                        }
                        catch (NumberFormatException e)
                        {
                            metrics.add(null);
                        }
                    }
                    intervals.add(metrics);
                }
                else if (mode == ReadingMode.AGGREGATES)
                {
                    String[] parts = line.split(":",2);
                    if (parts.length != 2)
                    {
                        continue;
                    }
                    json.put(parts[0].trim(), parts[1].trim());
                }
                else if (mode == ReadingMode.NEXTITERATION)
                {
                    //Wrap up the results of this test and append to the array.
                    json.put("metrics", Arrays.asList(StressGraph.HEADMETRICS));
                    json.put("test", option);
                    if (currentThreadCount == null)
                        json.put("revision", revision);
                    else
                        json.put("revision", String.format("%s - %s threads", revision, currentThreadCount));
                    json.put("command", stressArguments);
                    json.put("intervals", intervals);
                    stats.add(json);

                    //Start fresh for next iteration:
                    json = new JSONObject();
                    intervals = new JSONArray();
                    mode = ReadingMode.START;
                }
            }
        }
        catch (IOException e)
        {
            throw new RuntimeException("Couldn't read from temporary stress log file");
        }
        stats.add(json);
        return stats;
    }

    private JSONObject createJSONStats(JSONObject json)
    {
        try (InputStream logStream = new FileInputStream(statsLogFile))
        {
            JSONArray stats;
            if (json == null)
            {
                json = new JSONObject();
                stats = new JSONArray();
            }
            else
            {
                stats = (JSONArray) json.get("stats");
            }

            stats = parseLogStats(logStream, stats);

            json.put("title", title);
            json.put("stats", stats);
            return json;
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }
    
    
}