import sys
import os
import nltk
import json
from sumy.parsers.plaintext import PlaintextParser
from sumy.nlp.tokenizers import Tokenizer
from sumy.summarizers.lsa import LsaSummarizer

# Attempt to download required NLTK data quietly
try:
    nltk.download('punkt', quiet=True)
    nltk.download('averaged_perceptron_tagger', quiet=True)
    nltk.download('stopwords', quiet=True)
    nltk.download('universal_tagset', quiet=True)
    nltk.download('punkt_tab', quiet=True) # Added for newer NLTK versions
except:
    pass

def generate_summary(text, count=3):
    if not text.strip():
        return "Not enough text to generate a summary."
        
    try:
        parser = PlaintextParser.from_string(text, Tokenizer("english"))
        summarizer = LsaSummarizer()
        summary = summarizer(parser.document, count)
        result = " ".join([str(sentence) for sentence in summary])
        if not result:
            # Fallback to first few sentences if summarizer returns nothing
            sentences = text.split('.')
            result = ". ".join([s.strip() for s in sentences[:3] if s.strip()]) + "."
        
        # Clean newlines
        return result.replace('\n', ' ').strip()
    except Exception:
        return text[:300].replace('\n', ' ').strip() + "..."

def extract_tags(text):
    if not text.strip():
        return ["Empty"]
        
    try:
        # Simple extraction based on word frequency and POS tagging
        tokens = nltk.word_tokenize(text)
        pos_tags = nltk.pos_tag(tokens)
        
        # Filter for nouns and significant words
        from nltk.corpus import stopwords
        stop_words = set(stopwords.words('english'))
        
        keywords = []
        for word, pos in pos_tags:
            if pos.startswith('NN') and len(word) > 3 and word.lower() not in stop_words:
                keywords.append(word.capitalize())
        
        freq = nltk.FreqDist(keywords)
        top_tags = [word for word, count in freq.most_common(5)]
        return top_tags if top_tags else ["Document"]
    except Exception:
        return ["Document"]

def extract_key_points(text, count=5):
    if not text.strip():
        return ["No key points found."]
        
    try:
        # Use sumy for key points as well but maybe with more sentences
        parser = PlaintextParser.from_string(text, Tokenizer("english"))
        summarizer = LsaSummarizer()
        sentences = summarizer(parser.document, count)
        points = [str(s).replace('\n', ' ').strip() for s in sentences]
        
        if not points:
            # Fallback
            sentences = [s.strip() for s in text.split('.') if s.strip()]
            points = sentences[:count]
            
        return points
    except Exception:
        return ["Points could not be extracted."]

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(json.dumps({"error": "No file path provided"}))
        sys.exit(1)

    file_path = sys.argv[1]
    if not os.path.exists(file_path):
        print(json.dumps({"error": f"File {file_path} not found"}))
        sys.exit(1)

    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        if not content.strip():
            print(json.dumps({
                "summary": "No text content provided.",
                "tags": ["Empty"],
                "key_points": ["No text detected."]
            }))
            sys.exit(0)

        summary = generate_summary(content)
        tags = extract_tags(content)
        key_points = extract_key_points(content)
        
        output = {
            "summary": summary,
            "tags": tags,
            "keyPoints": key_points
        }
        print(json.dumps(output))
        
    except Exception as e:
        print(json.dumps({"error": str(e)}))
        sys.exit(1)
