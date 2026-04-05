FROM eclipse-temurin:21

WORKDIR /app

COPY . .

RUN javac VotingServer.java

CMD ["java", "VotingServer"]
