from flask import Flask, render_template, request, jsonify
import subprocess
import os
import time

app = Flask(__name__)
os.makedirs("static", exist_ok=True)

@app.route('/')
def index():
    return render_template('index.html')

@app.route('/api/tts', methods=['POST'])
def generate_tts():
    data = request.json
    text = data.get('text', '').strip()
    engine = data.get('engine', 'edge')
    voice = data.get('voice', 'hi-IN-SwaraNeural')
    rate = data.get('rate', '+0%')
    
    if not text:
        return jsonify({'error': 'Text input cannot be empty!'}), 400

    filename = f"audio_{int(time.time())}"
    
    try:
        if engine == 'edge':
            output_path = f"static/{filename}.mp3"
            cmd = ['edge-tts', '--voice', voice, '--rate', rate, '--text', text, '--write-media', output_path]
            subprocess.run(cmd, check=True)
            return jsonify({'url': f"/static/{filename}.mp3"})
            
        elif engine == 'piper':
            output_path = f"static/{filename}.wav"
            piper_cmd = f"echo '{text}' | ./piper/piper --model ./piper/hi_IN-priyamvada.onnx --output_file {output_path}"
            subprocess.run(piper_cmd, shell=True, check=True)
            return jsonify({'url': f"/static/{filename}.wav"})
            
    except Exception as e:
        return jsonify({'error': str(e)}), 500

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
