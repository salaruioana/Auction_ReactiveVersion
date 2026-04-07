#!/bin/bash

echo " Pornim sistemul de licitație Okazii..."

# Am definit calea ta de baza intr-o variabila ca sa pastram codul curat
BASE_DIR="/home/student/SD/Lab7/SD_Laborator_07/Okazii/out/artifacts"

# 1. Pornim backend-ul (Procesoarele)
echo "1️ Pornim MessageProcessor si BiddingProcessor..."
java -jar "$BASE_DIR/MessageProcessorMicroservice_jar/MessageProcessorMicroservice.jar" &
java -jar "$BASE_DIR/BiddingProcessorMicroservice_jar/BiddingProcessorMicroservice.jar" &

# Îi dăm sistemului 2 secunde să deschidă socket-urile TCP
sleep 2

# 2. Pornim Auctioneer-ul
echo "2️ Pornim AuctioneerMicroservice (Incepe cronometrul de 15 secunde!)..."
java -jar "$BASE_DIR/AuctioneerMicroservice_jar/AuctioneerMicroservice.jar" &

# Îi dăm 1 secundă să pornească complet
sleep 1

# 3. Pornim clienții (Bidderii)
NUMAR_BIDDERI=15 # Pune 100 cand vrei sa testezi cerinta 1 din tema
echo "3️ Pornim $NUMAR_BIDDERI Bidderi..."

for i in $(seq 1 $NUMAR_BIDDERI)
do
   java -jar "$BASE_DIR/BidderMicroservice_jar/BidderMicroservice.jar" &
done

echo " Asteptam finalizarea licitatiei (15 secunde)..."
wait

echo " Licitatia s-a incheiat cu succes!"