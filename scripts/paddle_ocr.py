import sys
import os
import cv2
import numpy as np
from paddleocr import PaddleOCR

# Re-initializing with standard high-quality settings
ocr = PaddleOCR(use_angle_cls=True, lang='en', show_log=False, use_gpu=False, enable_mkldnn=True)

def preprocess_image(image_path):
    img = cv2.imread(image_path)
    if img is None: return None
    gray = cv2.cvtColor(img, cv2.COLOR_BGR2GRAY)
    # Balanced sharpening
    kernel = np.array([[-1,-1,-1], [-1,9,-1], [-1,-1,-1]])
    sharpened = cv2.filter2D(gray, -1, kernel)
    return sharpened

def extract_text(image_path):
    if not os.path.exists(image_path):
        return f"Error: File {image_path} not found"
    
    # Process the image but keep it realistic
    processed_img = preprocess_image(image_path)
    temp_path = image_path + "_proc.jpg"
    if processed_img is not None:
        cv2.imwrite(temp_path, processed_img)
        path_to_read = temp_path
    else:
        path_to_read = image_path
        
    result = ocr.ocr(path_to_read)
    
    # Cleanup
    if os.path.exists(temp_path):
        try: os.remove(temp_path)
        except: pass
        
    if result is None or len(result) == 0:
        return "No text detected."
        
    lines = []
    for page in result:
        if page is None: continue
        # Sort by Y (top to bottom) then X (left to right)
        # We use a larger tolerance for Y to keep lines together
        page.sort(key=lambda x: (x[0][0][1], x[0][0][0]))
        
        current_y = -1
        current_line = []
        
        for element in page:
            y = element[0][0][1]
            text = element[1][0]
            # No more confidence filtering - we want the COMPLETE text
            
            if current_y == -1 or abs(y - current_y) < 15:
                current_line.append(text)
                if current_y == -1: current_y = y
            else:
                lines.append(" ".join(current_line))
                current_line = [text]
                current_y = y
        
        if current_line:
            lines.append(" ".join(current_line))
            
    return "\n".join(lines)

if __name__ == "__main__":
    if len(sys.argv) < 2: sys.exit(1)
    image_path = sys.argv[1]
    try:
        print(extract_text(image_path))
    except Exception as e:
        print(f"Error: {str(e)}")
