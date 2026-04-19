import sys
import os
import nltk
from sumy.parsers.plaintext import PlaintextParser
from sumy.nlp.tokenizers import Tokenizer
from sumy.summarizers.lsa import LsaSummarizer

# Attempt to download required NLTK data quietly
try:
    nltk.download('punkt', quiet=True)
    nltk.download('averaged_perceptron_tagger', quiet=True)
    nltk.download('stopwords', quiet=True)
    nltk.download('universal_tagset', quiet=True)
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
        return result if result else "Text content is too short for a structured summary."
    except Exception:
        return text[:200] + "..." # Fallback to first 200 chars

def extract_tags(text):
    if not text.strip():
        return ["Empty"]
        
    try:
        tokens = nltk.word_tokenize(text)
        pos_tags = nltk.pos_tag(tokens)
        # Extract nouns (NN) and proper nouns (NNP) as candidate tags
        keywords = [word for word, pos in pos_tags if pos.startswith('NN') and len(word) > 3]
        
        # Filter stopwords
        from nltk.corpus import stopwords
        stop_words = set(stopwords.words('english'))
        keywords = [w.capitalize() for w in keywords if w.lower() not in stop_words]
        
        freq = nltk.FreqDist(keywords)
        top_tags = [word for word, count in freq.most_common(5)]
        return top_tags if top_tags else ["Document"]
    except Exception:
        return ["Document"]

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Error: No file path provided")
        sys.exit(1)

    file_path = sys.argv[1]
    if not os.path.exists(file_path):
        print(f"Error: File {file_path} not found")
        sys.exit(1)

    try:
        with open(file_path, 'r', encoding='utf-8') as f:
            content = f.read()

        if not content.strip():
            print("No text content provided.")
            print("Empty")
            sys.exit(0)

        summary = generate_summary(content)
        tags = extract_tags(content)

        # Output Summary and Tags for Java to read
        print(summary)
        print(", ".join(tags))
    except Exception as e:
        print(f"Error during analysis: {str(e)}")
        sys.exit(1)
