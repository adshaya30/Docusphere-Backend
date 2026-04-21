import sys
import os
from paddleocr import PaddleOCR

# Initialize PaddleOCR
# use_textline_orientation=True to handle rotated text
# lang='en' for English documents
ocr = PaddleOCR(use_angle_cls=True, lang='en', show_log=False)

def extract_text(image_path):
    if not os.path.exists(image_path):
        return f"Error: File {image_path} not found"
        
    result = ocr.ocr(image_path)
    full_text = []
    
    if result is None or len(result) == 0:
        return "No text detected in the image."
        
    for page in result:
        if page is None: continue
        for line in page:
            if line is None or len(line) < 2: continue
            text_element = line[1][0]
            full_text.append(text_element)
            
    return "\n".join(full_text)

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print("Error: No image path provided")
        sys.exit(1)

    image_path = sys.argv[1]
    try:
        text = extract_text(image_path)
        print(text)
    except Exception as e:
        print(f"Error during OCR extraction: {str(e)}")
        sys.exit(1)
