package Server;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import java.util.Hashtable;

public class ServerConfig {

  private static final int DEFAULT_PORT = 6789;
  private static final int DEFAULT_NSELECTLOOPS = 2;

	private static final String commentRegex = "#.*";
	private static final String directiveRegex = "([^\\s]+)\\s*(.+)";
	private static final String sectionOpenRegex = "<([^/\\s>]+)\\s*([^>]+)?>";
	private static final String sectionCloseRegex = "</([^\\s>]+)\\s*>";

	private static final Matcher commentMatcher = Pattern.compile(commentRegex).matcher("");
	private static final Matcher directiveMatcher = Pattern.compile(directiveRegex).matcher("");
	private static final Matcher sectionOpenMatcher = Pattern.compile(sectionOpenRegex).matcher("");
	private static final Matcher sectionCloseMatcher = Pattern.compile(sectionCloseRegex).matcher("");

  public int port;
  public int nSelectLoops;
  public Hashtable<String, String> vHostNameToRoot;

  private enum ParseStates{GLOBAL, SCOPED}

  public ServerConfig() {
    port = DEFAULT_PORT;
    nSelectLoops = DEFAULT_NSELECTLOOPS;
    vHostNameToRoot = new Hashtable<>();
  }

  public int getPort() {
    return port;
  }

  public int getNSelectLoops() {
    return nSelectLoops;
  }

  public Hashtable<String, String> getVHostRoots() {
    return vHostNameToRoot;
  }

	public void parseConfigurationFile(String confFileName) {
    try (BufferedReader br = new BufferedReader(new FileReader(confFileName))) {

      ParseStates parseState = ParseStates.GLOBAL;

      String line;
      String documentRoot = "";
      String serverName = "";
      while ((line = br.readLine()) != null) {
        if (commentMatcher.reset(line).find()) {
          continue;
        }
        else if (parseState == ParseStates.GLOBAL && sectionOpenMatcher.reset(line).find()) {
          parseState = ParseStates.SCOPED;
        }
        else if (parseState == ParseStates.SCOPED && sectionCloseMatcher.reset(line).find()) {
          parseState = ParseStates.GLOBAL;
          vHostNameToRoot.put(documentRoot, serverName);
        }
        else if (directiveMatcher.reset(line).find()) {
          String name = directiveMatcher.group(1);
          String val = directiveMatcher.group(2);
          if (name.equals("Listen")) {
            port = Integer.parseInt(val);
          }
          else if (name.equals("nSelectLoops")) {
            nSelectLoops = Integer.parseInt(val);
          }
          else if (name.equals("DocumentRoot")) {
            documentRoot = val;
          }
          else if (name.equals("ServerName")) {
            serverName = val;
          }
          else {
            throw new IOException("\tInvalid Directive");
          }

        } // end directive case
      } // end while loop

    } catch (Exception e) {
      System.out.println("\tInvalid configuration file!");
    } // end try catch
	} // end parseConfigurationFile
  
}
