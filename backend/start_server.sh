#!/bin/bash
echo ">> [Setup] Starting Server..."

# Install dependencies
pip3 install -r requirements.txt

# Run Flask server
python3 app.py
