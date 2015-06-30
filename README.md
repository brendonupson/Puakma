# Puakma
Java Application Server

This software began as a research project on/around 13 June 2001 as a means of developing a rapid web application development platform. The server core supports a plugin architecture to allow other "addin" modules to be added so the server can easily add fifferent personalities. Current addins include: HTTP (web server), BOOSTER (reverse proxy), AGENDA (scheduled tasks), MAILER (outbound smtp mail), STATS (server statistics), SETUP (system database creation).

The main server code is packaged into puakma.jar and is very small (~600Kb). Puakma requires a system database (relational) which can be HSQLDB, mySQL, postgreSQL. The database stores the actual web applications.

The web framework was orginally based on the PRG web pattern: Post, Redirect, Get. https://en.wikipedia.org/wiki/Post/Redirect/Get but it's up to the individual developer to implement whatever pattern they ultimately choose.

To develop apps on top of this framework we recommend the Vortex IDE (eclipse based). For underlying server, we recommend Linux and JRE 6 or above.
