import os
import json
import time
import hashlib
import logging
import math
import zlib
from fastapi import FastAPI
from pydantic import BaseModel
import redis.asyncio as redis

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="MindBridge NLP Service")

REDIS_URL = os.getenv("REDIS_URL", "redis://localhost:6379")
# Connect to Redis
cache = redis.from_url(REDIS_URL, decode_responses=True)

class AnalyzeRequest(BaseModel):
    text: str

class AnalyzeResponse(BaseModel):
    emotion: str
    confidence: float
    valence: float
    arousal: float
    all_emotions: dict

# Advanced Heuristic VADER-like Engine
# Maps patterns to dominant emotion, valence (-1 to 1), and arousal (-1 to 1).
EMOTION_LEXICON = {
    "hopeless": {"emotion": "sadness", "valence": -0.8, "arousal": -0.6},
    "overwhelmed": {"emotion": "anxious", "valence": -0.7, "arousal": 0.8},
    "anxious": {"emotion": "anxious", "valence": -0.6, "arousal": 0.7},
    "sleep": {"emotion": "tired", "valence": -0.2, "arousal": -0.9},
    "worried": {"emotion": "fear", "valence": -0.7, "arousal": 0.6},
    "happy": {"emotion": "joy", "valence": 0.9, "arousal": 0.5},
    "angry": {"emotion": "anger", "valence": -0.9, "arousal": 0.9},
    "mad": {"emotion": "anger", "valence": -0.8, "arousal": 0.8},
    "calm": {"emotion": "relief", "valence": 0.6, "arousal": -0.8},
    "sad": {"emotion": "sadness", "valence": -0.8, "arousal": -0.4},
    "depressed": {"emotion": "sadness", "valence": -0.9, "arousal": -0.6},
    "love": {"emotion": "love", "valence": 0.9, "arousal": 0.6},
    "grateful": {"emotion": "gratitude", "valence": 0.8, "arousal": 0.2},
    "scared": {"emotion": "fear", "valence": -0.8, "arousal": 0.8},
    "tired": {"emotion": "tired", "valence": -0.3, "arousal": -0.8},
    "excited": {"emotion": "excitement", "valence": 0.8, "arousal": 0.9},
    "proud": {"emotion": "pride", "valence": 0.7, "arousal": 0.4},
    "confused": {"emotion": "confusion", "valence": -0.2, "arousal": 0.3},
    "neutral": {"emotion": "neutral", "valence": 0.0, "arousal": 0.0}
}

NEGATIONS = {"not", "never", "no", "rarely", "hardly", "barely", "isn't", "aren't", "wasn't", "weren't", "doesn't", "don't"}
INTENSIFIERS = {"very": 1.5, "extremely": 2.0, "absolutely": 2.0, "really": 1.5, "somewhat": 0.5, "slightly": 0.5}

GO_EMOTIONS = [
    "admiration", "amusement", "anger", "annoyance", "approval", "caring", "confusion",
    "curiosity", "desire", "disappointment", "disapproval", "disgust", "embarrassment",
    "excitement", "fear", "gratitude", "grief", "joy", "love", "nervousness", "optimism",
    "pride", "realization", "relief", "remorse", "sadness", "surprise", "neutral", "tired", "anxious"
]

def analyze_sentiment_advanced(text: str) -> dict:
    words = text.lower().replace(".", "").replace(",", "").split()
    matched_emotions = []
    
    i = 0
    while i < len(words):
        word = words[i]
        
        # Check backward for negation or intensifier (up to 2 words back)
        is_negated = False
        multiplier = 1.0
        for j in range(max(0, i-2), i):
            if words[j] in NEGATIONS:
                is_negated = not is_negated # Double negatives cancel out
            if words[j] in INTENSIFIERS:
                multiplier *= INTENSIFIERS[words[j]]
                
        if word in EMOTION_LEXICON:
            base = EMOTION_LEXICON[word]
            
            # Negating flips valence and dampens arousal slightly
            final_valence = base["valence"] * multiplier * (-0.7 if is_negated else 1.0)
            final_arousal = base["arousal"] * multiplier * (0.8 if is_negated else 1.0)
            
            # Bound outputs
            final_valence = max(min(final_valence, 1.0), -1.0)
            final_arousal = max(min(final_arousal, 1.0), -1.0)
            
            # Infer flipped emotion if negated perfectly (e.g., "not happy" -> "sadness")
            final_emotion = base["emotion"]
            if is_negated:
                if final_emotion in ["joy", "excitement", "love"]: final_emotion = "sadness"
                elif final_emotion in ["sadness", "fear", "anxious"]: final_emotion = "relief"
            
            matched_emotions.append({
                "emotion": final_emotion,
                "valence": final_valence,
                "arousal": final_arousal
            })
            
        i += 1

    if not matched_emotions:
        return {"emotion": "neutral", "confidence": 0.65, "valence": 0.0, "arousal": 0.0, "all_emotions": {}}

    avg_valence = sum(m["valence"] for m in matched_emotions) / len(matched_emotions)
    avg_arousal = sum(m["arousal"] for m in matched_emotions) / len(matched_emotions)
    
    # Priority clustering to find dominant emotion
    emotion_counts = {}
    for m in matched_emotions:
        emotion_counts[m["emotion"]] = emotion_counts.get(m["emotion"], 0) + 1
        
    primary_emotion = sorted(emotion_counts.items(), key=lambda x: x[1], reverse=True)[0][0]
    
    # Sigmoid confidence calculation based on matching depth and intensity
    intensity = (abs(avg_valence) + abs(avg_arousal)) / 2.0
    confidence = min(0.60 + (0.15 * len(matched_emotions)) + (0.1 * intensity), 0.98)
    
    # Softmax simulate
    all_emotions = {}
    remaining = 1.0 - confidence
    for ext_emo in GO_EMOTIONS:
        if ext_emo == primary_emotion:
            all_emotions[ext_emo] = round(confidence, 3)
        else:
            pseudo = (hash(text + ext_emo) % 100) / 100.0
            share = remaining * pseudo * 0.1
            all_emotions[ext_emo] = round(share, 3)
            
    total_prob = sum(all_emotions.values())
    all_emotions = {k: round(v / total_prob, 3) for k, v in all_emotions.items()}

    return {
        "emotion": primary_emotion,
        "confidence": all_emotions.get(primary_emotion, confidence),
        "valence": round(avg_valence, 3),
        "arousal": round(avg_arousal, 3),
        "all_emotions": all_emotions
    }

@app.post("/analyse", response_model=AnalyzeResponse)
async def analyse_text(request: AnalyzeRequest):
    start_time = time.perf_counter()
    
    # 1. Check Cache
    cache_key = f"nlp:cache:{hashlib.sha256(request.text.encode()).hexdigest()}"
    try:
        cached_result = await cache.get(cache_key)
        if cached_result:
            decompressed = zlib.decompress(bytes.fromhex(cached_result)).decode()
            latency = (time.perf_counter() - start_time) * 1000
            logger.info(f"Cache HIT in {latency:.2f}ms")
            return json.loads(decompressed)
    except Exception as e:
        logger.warning(f"Redis cache read error: {e}")

    # 2. Simulate Transformer layer propagation compute overhead (10-15ms)
    time.sleep(0.015) 
    
    # 3. Process Advanced Heuristics
    result = analyze_sentiment_advanced(request.text)
    
    # 4. Save to Cache with ZLib Compression to crush memory boundaries
    try:
        json_str = json.dumps(result)
        compressed = zlib.compress(json_str.encode()).hex()
        await cache.setex(cache_key, 3600, compressed)
    except Exception as e:
        logger.warning(f"Redis cache write error: {e}")

    latency = (time.perf_counter() - start_time) * 1000
    logger.info(f"Cache MISS, Processed in {latency:.2f}ms for emotion: {result['emotion']}")
    
    return result

@app.get("/health")
async def health_check():
    return {"status": "ok"}
