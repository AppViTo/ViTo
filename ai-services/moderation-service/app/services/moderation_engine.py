from typing import List, Dict, Any, Optional
from datetime import datetime
import re
import logging

logger = logging.getLogger(__name__)

class ModerationEngine:
    def __init__(self):
        self.profanity_list = self._load_profanity_list()
        self.spam_patterns = self._load_spam_patterns()
        self.banned_words = set()
    
    async def moderate_content(
        self,
        content_id: str,
        content_type: str,
        content_text: Optional[str] = None,
        content_url: Optional[str] = None,
        user_id: str = None
    ) -> Dict[str, Any]:
        """Moderate content for safety and policy compliance."""
        try:
            flags = []
            categories = []
            confidence = 1.0
            
            # Text moderation
            if content_text:
                text_result = await self._moderate_text(content_text)
                flags.extend(text_result["flags"])
                categories.extend(text_result["categories"])
                confidence = min(confidence, text_result["confidence"])
            
            # Image moderation (placeholder)
            if content_type in ["image", "video"] and content_url:
                image_result = await self._moderate_image(content_url)
                flags.extend(image_result["flags"])
                categories.extend(image_result["categories"])
                confidence = min(confidence, image_result["confidence"])
            
            # Determine if safe
            is_safe = len(flags) == 0 or confidence < 0.5
            
            # Determine action
            action = self._determine_action(flags, confidence)
            
            return {
                "content_id": content_id,
                "is_safe": is_safe,
                "confidence": confidence,
                "flags": list(set(flags)),
                "categories": list(set(categories)),
                "recommended_action": action,
                "details": {
                    "content_type": content_type,
                    "user_id": user_id,
                    "moderated_at": datetime.now().isoformat()
                }
            }
            
        except Exception as e:
            logger.error(f"Error moderating content: {e}")
            return {
                "content_id": content_id,
                "is_safe": True,
                "confidence": 0.0,
                "flags": [],
                "categories": [],
                "recommended_action": "approve",
                "details": {"error": str(e)}
            }
    
    async def report_content(
        self,
        content_id: str,
        content_type: str,
        reason: str,
        reporter_id: str,
        details: Optional[str] = None
    ) -> Dict[str, Any]:
        """Process a user report for content."""
        try:
            # Validate reason
            valid_reasons = [
                "spam", "harassment", "hate_speech", "violence",
                "nudity", "misinformation", "copyright", "other"
            ]
            
            if reason not in valid_reasons:
                reason = "other"
            
            # Create report
            report = {
                "content_id": content_id,
                "content_type": content_type,
                "reason": reason,
                "reporter_id": reporter_id,
                "details": details,
                "status": "pending",
                "created_at": datetime.now().isoformat()
            }
            
            # Store report (placeholder - would use database in production)
            report_id = f"report_{content_id}_{datetime.now().timestamp()}"
            
            # Check if content should be auto-removed
            await self._check_auto_moderation(content_id, reason)
            
            return {
                "report_id": report_id,
                "status": "submitted",
                "message": "Report submitted successfully"
            }
            
        except Exception as e:
            logger.error(f"Error processing report: {e}")
            return {
                "report_id": None,
                "status": "error",
                "message": "Failed to submit report"
            }
    
    async def _moderate_text(self, text: str) -> Dict[str, Any]:
        """Moderate text content."""
        flags = []
        categories = []
        confidence = 1.0
        
        text_lower = text.lower()
        
        # Profanity check
        if self.profanity_list:
            for word in self.profanity_list:
                if word in text_lower:
                    flags.append("profanity")
                    categories.append("language")
                    confidence = min(confidence, 0.8)
                    break
        
        # Spam detection
        spam_score = self._detect_spam(text)
        if spam_score > 0.7:
            flags.append("spam")
            categories.append("quality")
            confidence = min(confidence, spam_score)
        
        # Harassment detection (basic)
        harassment_patterns = [
            r"kill yourself",
            r"die",
            r"hate you",
            r"stupid",
            r"idiot"
        ]
        
        for pattern in harassment_patterns:
            if re.search(pattern, text_lower):
                flags.append("harassment")
                categories.append("safety")
                confidence = min(confidence, 0.9)
                break
        
        # URL spam detection
        url_count = len(re.findall(r'http[s]?://', text))
        if url_count > 3:
            flags.append("spam")
            categories.append("quality")
            confidence = min(confidence, 0.85)
        
        return {
            "flags": flags,
            "categories": categories,
            "confidence": confidence
        }
    
    async def _moderate_image(self, url: str) -> Dict[str, Any]:
        """Moderate image content (placeholder for actual ML model)."""
        # In production, this would call an external API like:
        # - Google Cloud Vision SafeSearch
        # - AWS Rekognition Content Moderation
        # - Azure Content Moderator
        
        return {
            "flags": [],
            "categories": [],
            "confidence": 1.0
        }
    
    def _detect_spam(self, text: str) -> float:
        """Detect spam content."""
        spam_score = 0.0
        
        # Check for excessive caps
        if len(text) > 0:
            caps_ratio = sum(1 for c in text if c.isupper()) / len(text)
            if caps_ratio > 0.5:
                spam_score += 0.3
        
        # Check for repeated characters
        if re.search(r'(.)\1{3,}', text):
            spam_score += 0.2
        
        # Check for spam patterns
        for pattern in self.spam_patterns:
            if re.search(pattern, text.lower()):
                spam_score += 0.4
                break
        
        return min(spam_score, 1.0)
    
    def _determine_action(self, flags: List[str], confidence: float) -> str:
        """Determine moderation action based on flags and confidence."""
        if not flags:
            return "approve"
        
        if confidence < 0.5:
            return "approve"
        
        if "violence" in flags or "hate_speech" in flags:
            return "reject"
        
        if confidence > 0.8:
            return "reject"
        
        return "flag_for_review"
    
    async def _check_auto_moderation(self, content_id: str, reason: str):
        """Check if content should be auto-moderated."""
        # In production, this would:
        # 1. Check user's report history
        # 2. Check content's report count
        # 3. Auto-ban if threshold exceeded
        pass
    
    def _load_profanity_list(self) -> List[str]:
        """Load profanity word list."""
        # Placeholder - would load from file or database in production
        return ["badword1", "badword2", "badword3"]
    
    def _load_spam_patterns(self) -> List[str]:
        """Load spam detection patterns."""
        return [
            r"buy now",
            r"click here",
            r"free money",
            r"limited time offer",
            r"act now"
        ]
