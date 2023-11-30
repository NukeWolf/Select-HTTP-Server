package Server;
import java.io.File;
import java.util.Base64;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class Htaccess {
    private String authType;
    public String authName;
    private String user_encoded;
    private String pass_encoded;
    private boolean authenticated;

    public Htaccess(File f){
        parseFile(f);
        authenticated = false;
    }

    public void parseFile(File f){
        try{
            Scanner in = new Scanner(f);
            while(in.hasNextLine()){
                String matchingRegex = "(\\w+) (.+)";
                String line = in.nextLine();

                Pattern pattern = Pattern.compile(matchingRegex);
                Matcher matcher = pattern.matcher(line);
                matcher.find();
                String key = matcher.group(1);
                String val = matcher.group(2);
                Debug.DEBUG("HTKey: " +key + " HT Val: " + val);

                if(key.equals("AuthType")){
                    authType = val;
                }
                if(key.equals("AuthName")){
                    authName = val;
                }
                if(key.equals("User")){
                    user_encoded = val;
                }
                if(key.equals("Password")){
                    pass_encoded = val;
                }
            }
            in.close();
        }
        catch (Exception e){
            e.printStackTrace();
        }
    }
    public boolean authenticateToken(String token){
        byte[] decodedBytes_user = Base64.getDecoder().decode(user_encoded);
        String decodedUser = new String(decodedBytes_user);

        byte[] decodedBytes_pass = Base64.getDecoder().decode(pass_encoded);
        String decodedPass = new String(decodedBytes_pass);

        String combined = decodedUser + ":" + decodedPass;
        String encodedString = Base64.getEncoder().withoutPadding().encodeToString(combined.getBytes());


        String matchingRegex = "(\\w+) (.+)";
        Pattern pattern = Pattern.compile(matchingRegex);
        Matcher matcher = pattern.matcher(token);

        matcher.find();
        String type = matcher.group(1);
        String encoded_token = matcher.group(2);
        
        authenticated = encodedString.equals(encoded_token) && type.equals(authType);
        return authenticated;
    }
    public String getAuthType(){
        return authType;
    }
    public String getUser(){
        byte[] decodedBytes_user = Base64.getDecoder().decode(user_encoded);
        String decodedUser = new String(decodedBytes_user);
        return decodedUser;
    }
}
