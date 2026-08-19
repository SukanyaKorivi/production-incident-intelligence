import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.Random;
public class EventSenderClient {
    public static void main(String[] args) {

        HttpClient client = HttpClient.newHttpClient();
        Random random=new Random();

        String[] healthyLogs ={ """
            {
                "serviceName": "payment-service",
                "logLevel": "INFO",
                "message": "Transaction cleared successfully"
            }
            ""","""
            {
                "serviceName": "auth-service",
                "logLevel": "INFO",
                "message": "User session token renewed"
            }
            ""","""
            {
                "serviceName": "inventory-service",
                "logLevel": "INFO",
                "message": "Stock count updated for product-id"
            }
            """
        };
        String[] errorChecks={"""
            {
                "serviceName": "payment-service",
                "logLevel": "ERROR",
                "message": "DATABASE CONNECTION FAILURE:Pool exhausted"
            }
            ""","""
            {
                "serviceName": "auth-service",
                "logLevel": "ERROR",
                "message": "AUTHENTICATION SERVER TIMEOUT"
            }
            """};
        while(true){
            int i=0;
            while(i<60){

                try {
                    String jsonPayload=healthyLogs[random.nextInt(3)];

                    HttpRequest request = HttpRequest.newBuilder()
                            .uri(URI.create("http://localhost:8080/events"))
                            .header("Content-Type", "application/json")
                            .POST(HttpRequest.BodyPublishers.ofString(jsonPayload))
                            .build();

                    HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                    System.out.println("HTTP Status Code: " + response.statusCode());
                    System.out.println("Database Response Body: " + response.body());
                    Thread.sleep(1000);
                } catch (Exception e) {
                    System.err.println("Failed to connect to localhost server: " + e.getMessage());
                }
                i++;
            }
            if(i==60){
                int totalBurstLogs = 15 + random.nextInt(6);
                System.out.println(" CRASH DETECTED! Firing a burst of " + totalBurstLogs + " error logs now...");

                    for(int j=0;j<=totalBurstLogs;j++){
                        try{
                        Instant systemTimeNow = Instant.now();
                        String dynamicJsonPayload=errorChecks[0];
                        if(j>=15){
                            dynamicJsonPayload=errorChecks[1];}

                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create("http://localhost:8080/events"))
                                .header("Content-Type", "application/json")
                                .POST(HttpRequest.BodyPublishers.ofString(dynamicJsonPayload))
                                .build();
                        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

                        System.out.println("   [Burst " + j + "/" + totalBurstLogs + "] Sent ERROR log at " + systemTimeNow);
                            System.out.println("HTTP Status Code: " + response.statusCode());
                            System.out.println("Database Response Body: " + response.body());


                    } catch(Exception e) {

                        System.err.println("Burst error: " + e.getMessage());
                    try{
                        Thread.sleep(150);
                    } catch (InterruptedException ie) {
                        break;
                    }
                    }
                }
            }
        }
    }
}