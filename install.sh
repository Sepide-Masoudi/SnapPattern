#!/bin/bash

# Exit immediately if a command fails
set -e

# Define green color
GREEN='\033[0;32m'
NC='\033[0m' # No Color

echo -e "${GREEN}📥 Downloading Apache JMeter...${NC}"
curl -LO https://dlcdn.apache.org/jmeter/binaries/apache-jmeter-5.6.3.tgz

echo -e "${GREEN}📦 Extracting JMeter package...${NC}"
tar -xzf apache-jmeter-5.6.3.tgz

echo -e "${GREEN}📦 Removing JMeter tar${NC}"
rm -rf apache-jmeter-5.6.3.tgz

echo -e "${GREEN}🐍 Creating Python virtual environment...${NC}"
python3 -m venv Python/env

echo -e "${GREEN}⚙️ Activating virtual environment...${NC}"
source Python/env/bin/activate

echo -e "${GREEN}📚 Installing Python dependencies...${NC}"
pip install --upgrade pip
pip install -r Python/requirements.txt

echo -e "${GREEN}✅ Setup complete!${NC}"
