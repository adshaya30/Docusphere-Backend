import sys
import os
import nltk
import json
import math
from sumy.parsers.plaintext import PlaintextParser
from sumy.nlp.tokenizers import Tokenizer
from sumy.summarizers.text_rank import TextRankSummarizer

# Essential NLTK data
try:
    nltk.data.find('tokenizers/punkt')
    nltk.data.find('corpora/stopwords')
except LookupError:
    try:
        nltk.download('punkt', quiet=True)
        nltk.download('stopwords', quiet=True)
    except:
        pass

def generate_full_summary(text):
    """
    Produces a DEEP and COMPLETE summary by picking a high volume 
    of representative sentences across the entire document.
    """
    words = text.split()
    word_count = len(words)
    
    # Scale summary length based on document size to ensure "completeness"
    # For a large document, we want at least 15-20 sentences.
    target_sentences = max(10, min(25, word_count // 40))
    
    try:
        parser = PlaintextParser.from_string(text, Tokenizer("english"))
        summarizer = TextRankSummarizer()
        # TextRank is slower but much higher quality for long documents
        summary = summarizer(parser.document, target_sentences)
        
        result = " ".join([str(sentence) for sentence in summary])
        return result.strip()
    except:
        # Fallback to a large chunk of text if summarizer fails
        sentences = text.split('.')
        return ". ".join(sentences[:target_sentences]) + "."

def extract_detailed_key_points(text):
    """
    Extracts up to 15 key points to match the "Image 1" quality.
    """
    try:
        parser = PlaintextParser.from_string(text, Tokenizer("english"))
        summarizer = TextRankSummarizer()
        sentences = summarizer(parser.document, 15)
        
        points = []
        for s in sentences:
            p = str(s).strip()
            # Ensure points are long and informative
            if len(p) > 40 and p not in points:
                points.append(p)
                
        return points[:12]
    except:
        return ["Details could not be extracted."]

if __name__ == "__main__":
    if len(sys.argv) < 2: sys.exit(1)

    file_path = sys.argv[1]
    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        # Generate High-Volume Output
        summary = generate_full_summary(content)
        key_points = extract_detailed_key_points(content)
        tags = [w.capitalize() for w in nltk.word_tokenize(content) if len(w) > 6 and w.isalnum()][:8]
        
        word_count = len(content.split())
        reading_time = math.ceil(word_count / 200)

        output = {
            "summary": summary,
            "tags": tags,
            "keyPoints": key_points,
            "metadata": {
                "wordCount": word_count,
                "readingTime": f"{reading_time} min read"
            }
        }
        print(json.dumps(output))
        
    except Exception as e:
        print(json.dumps({"error": str(e)}))
        sys.exit(1)
