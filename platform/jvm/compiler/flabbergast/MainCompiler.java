package flabbergast;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

class AutoWriteClassVisitor extends ClassVisitor {

    private ClassWriter writer;

    File target;

    public AutoWriteClassVisitor(ClassWriter writer) {
        super(Opcodes.ASM4, writer);
        this.writer = writer;
    }

    @Override
    public void visit(int version, int access, String class_name,
                      String signature, String super_name, String[] interfaces) {
        String path = class_name.replace('/', File.separatorChar) + ".class";
        target = new File(path);
        super.visit(version, access, class_name, signature, super_name,
                    interfaces);
    }

    @Override
    public void visitEnd() {
        super.visitEnd();
        try {
            target.getParentFile().mkdirs();
            FileOutputStream output = new FileOutputStream(target);
            output.write(writer.toByteArray());
            output.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}

public class MainCompiler {

    private static String removeExtensions(String filename,
                                           String... extensions) {
        for (String extension : extensions) {
            if (filename.endsWith(extension)) {
                return filename.substring(0,
                                          filename.length() - extension.length());
            }
        }
        return filename;
    }
    public static void main(String[] args) {
        Options options = new Options();
        options.addOption("t", "trace-parsing", false,
                          "Produce a trace of the parse process.");
        options.addOption("F", "no-frames", false,
                          "Do not compute frames. This class will not work. For debugging the compiler.");
        options.addOption("h", "help", false, "Show this message and exit");
        CommandLineParser cl_parser = new GnuParser();
        final CommandLine result;

        try {
            result = cl_parser.parse(options, args);
        } catch (ParseException e) {
            System.err.println(e.getMessage());
            System.exit(1);
            return;
        }

        if (result.hasOption('h')) {
            HelpFormatter formatter = new HelpFormatter();
            System.err
            .println("Compile a Flabbergast file for use as a library.");
            formatter.printHelp("gnu", options);
            System.exit(1);
        }

        String[] files = result.getArgs();
        if (files.length == 0) {
            System.err
            .println("Perhaps you wish to compile some source files?");
            System.exit(1);
            return;
        }
        ErrorCollector collector = new ConsoleCollector();
        CompilationUnit<Boolean> unit = new WriterCompilationUnit(
            result.hasOption('F'));
        for (String filename : files) {
            try {
                Parser parser = Parser.open(filename);
                parser.setTrace(result.hasOption('t'));
                String file_root = ("flabbergast/library/" + removeExtensions(
                                        filename, ".o_0", ".jo_0"))
                                   .replace(File.separatorChar, '/')
                                   .replaceAll("[/.]+", "/").replace('-', '_');
                parser.parseFile(collector, unit, file_root);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}
