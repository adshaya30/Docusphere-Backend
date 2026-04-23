import os
import sys
import json
from flask import Flask, request, jsonify
from paddleocr import PaddleOCR
import nltk
from sumy.parsers.plaintext import PlaintextParser
from sumy.nlp.tokenizers import Tokenizer
from sumy.summarizers.lex_rank import LexRankSummarizer
from nltk.corpus import stopwords
import re

app = Flask(__name__)

# Initialize PaddleOCR once
print("Initializing PaddleOCR...")
ocr = PaddleOCR(use_angle_cls=True, lang='en', show_log=False, use_gpu=False, enable_mkldnn=True, cpu_threads=4)

# Initialize NLTK
print("Initializing NLTK...")
try:
    nltk.data.find('tokenizers/punkt')
    nltk.data.find('taggers/averaged_perceptron_tagger')
    nltk.data.find('corpora/stopwords')
    nltk.data.find('taggers/universal_tagset')
    nltk.data.find('tokenizers/punkt_tab')
except LookupError:
    nltk.download('punkt', quiet=True)
    nltk.download('averaged_perceptron_tagger', quiet=True)
    nltk.download('stopwords', quiet=True)
    nltk.download('universal_tagset', quiet=True)
    nltk.download('punkt_tab', quiet=True)

def clean_text(text):
    # Remove excessive whitespace and fix broken lines
    text = re.sub(r'\s+', ' ', text)
    # Basic cleaning of common OCR noise
    text = re.sub(r'[|\\_]', '', text)
    return text.strip()

def generate_summary(text):
    if not text.strip():
        return "Not enough text to generate a summary."
    try:
        # Dynamic count: ~10% of sentences, but between 3 and 10
        sentence_count = max(3, min(10, len(text.split('.')) // 10))
        
        parser = PlaintextParser.from_string(text, Tokenizer("english"))
        summarizer = LexRankSummarizer()
        summary = summarizer(parser.document, sentence_count)
        result = " ".join([str(sentence) for sentence in summary])
        if not result:
            sentences = text.split('.')
            result = ". ".join([s.strip() for s in sentences[:3] if s.strip()]) + "."
        return result.replace('\n', ' ').strip()
    except Exception:
        return text[:500].replace('\n', ' ').strip() + "..."

def extract_tags(text):
    if not text.strip():
        return ["Empty"]
    try:
        tokens = nltk.word_tokenize(text)
        pos_tags = nltk.pos_tag(tokens)
        stop_words = set(stopwords.words('english'))
        # Added more noise words common in OCR
        noise_words = {'page', 'date', 'time', 'total', 'amount', 'document'}
        
        keywords = []
        for word, pos in pos_tags:
            word_lower = word.lower()
            if pos.startswith('NN') and len(word) > 3 and word_lower not in stop_words and word_lower not in noise_words:
                keywords.append(word.capitalize())
        
        freq = nltk.FreqDist(keywords)
        # Increased to 7 tags for more completeness
        top_tags = [word for word, count in freq.most_common(7)]
        return top_tags if top_tags else ["Document"]
    except Exception:
        return ["Document"]

def extract_key_points(text):
    if not text.strip():
        return ["No key points found."]
    try:
        # Key points count also dynamic
        point_count = max(5, min(12, len(text.split('.')) // 8))
        
        parser = PlaintextParser.from_string(text, Tokenizer("english"))
        summarizer = LexRankSummarizer()
        sentences = summarizer(parser.document, point_count)
        points = [str(s).replace('\n', ' ').strip() for s in sentences]
        if not points:
            sentences = [s.strip() for s in text.split('.') if s.strip()]
            points = sentences[:5]
        return points
    except Exception:
        return ["Points could not be extracted."]

@app.route('/process', methods=['POST'])
def process():
    data = request.get_json()
    if not data or 'image_path' not in data:
        return jsonify({"error": "No image_path provided"}), 400

    image_path = data['image_path']
    if not os.path.exists(image_path):
        return jsonify({"error": f"File {image_path} not found"}), 404

    try:
        # OCR
        result = ocr.ocr(image_path)
        full_text = []
        if result and len(result) > 0:
            for page in result:
                if page is None: continue
                for line in page:
                    if line is None or len(line) < 2: continue
                    full_text.append(line[1][0])
        
        raw_text = "\n".join(full_text)
        extracted_text = clean_text(raw_text)
        
        if not extracted_text.strip():
            return jsonify({
                "extractedText": "",
                "summary": "No text detected.",
                "tags": ["Empty"],
                "keyPoints": ["No text detected."]
            })

        # Analysis
        summary = generate_summary(extracted_text)
        tags = extract_tags(extracted_text)
        key_points = extract_key_points(extracted_text)
        
        return jsonify({
            "extractedText": extracted_text,
            "summary": summary,
            "tags": tags,
            "keyPoints": key_points
        })
    except Exception as e:
        return jsonify({"error": str(e)}), 500

if __name__ == "__main__":
    app.run(port=5000)
