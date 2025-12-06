#!/bin/bash
echo ">> [Setup] Starting Server..."

# Install dependencies
pip install -r requirements.txt

# Run Flask server
python app.py
