# Simple Server
Youtube demo:
https://youtu.be/-8CvUc0iGI0

## Description
This project is a basic server-client app without the use of any frameworks.
The Clients are able to query the server. Register a new account, 
login and message other logged in clients.

### The Server:
 - Uses Gson to format JSON messages
 - Accepts and processes and responds with JSON-formatted requests
 - Responds with a JSON message
 - Is able to register and login registered clients
 - Utilises Threads to handle multiple clients
 - Is able to process direct messages between clients
 - Saves registered users to a local file when shutdown
 - Saves client's direct messages (in real time) to a local file


### The Client:
 - Uses Gson to format JSON messages
 - Automatically connects to the server
 - Sends JSON messages to the server
 - Utilises Threads to listen to server responses


