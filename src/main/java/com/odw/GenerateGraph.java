package com.odw;

import java.io.File;

import net.sourceforge.argparse4j.ArgumentParsers;
import net.sourceforge.argparse4j.inf.ArgumentParser;
import net.sourceforge.argparse4j.inf.ArgumentParserException;
import net.sourceforge.argparse4j.inf.Namespace;

public class GenerateGraph {

    public static void main(String[] args) {
        ArgumentParser parser = ArgumentParsers
                .newArgumentParser("")
                .defaultHelp(true)
                .description("Generate graph according to specific stress log.");

        parser.addArgument("-m", "--model")
                .required(true)
                .choices("WRITE", "READ", "MIXED", "COUNTER_WRITE",
                        "COUNTER_READ", "USER").setDefault("WRITE")
                .help("Specify the stress test model that you just have run.");

        parser.addArgument("-i", "--input").required(true)
                .help("The file path of stress log that you want to anlyse.");

        parser.addArgument("-t", "--title").required(false)
                .help("The test's tile name. Input file's name as default.");

        parser.addArgument("-c", "--commandLine")
                .required(false)
                .help("The command line that you used to run this stress test.");

        parser.addArgument("-v", "--vision").setDefault("1.0").required(false)
                .help("The version No.");

        parser.addArgument("-o", "--output").required(true)
                .help("The file path of anlysis result.");
        
        parser.addArgument("-sa", "--stressAgrs").required(false).setDefault("").required(true)
        .help("The file path of anlysis result.");
        

        Namespace ns = null;

        try {
            ns = parser.parseArgs(args);
        } catch (ArgumentParserException e) {
            parser.handleError(e);
            System.exit(1);
        }

        String input = ns.getString("input");
        String output = ns.getString("output");

        if (!new File(input).isFile()) {
            System.err.printf("The defined file %s don't exist ", input);
        }

        new StressGraph(ns.getString("model"), output, ns.getString("title"),
                ns.getString("stressAgrs"), ns.getString("vision"), input).generateGraph();

    }

}
