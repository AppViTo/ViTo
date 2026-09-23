from typing import List, Dict, Any, Optional
from datetime import datetime, timedelta
import numpy as np
from collections import defaultdict
import logging

logger = logging.getLogger(__name__)

class RecommendationEngine:
    def __init__(self):
        self.user_profiles = {}
        self.event_features = {}
        self.interaction_matrix = {}
    
    async def get_event_recommendations(self, user_id: str, limit: int = 10) -> List[Dict[str, Any]]:
        """Get personalized event recommendations for a user."""
        try:
            # Get user preferences
            user_prefs = await self._get_user_preferences(user_id)
            
            # Get candidate events
            candidates = await self._get_candidate_events(user_id)
            
            # Score events
            scored_events = []
            for event in candidates:
                score = await self._calculate_event_score(user_prefs, event)
                if score > 0.1:
                    scored_events.append({
                        "event_id": event["id"],
                        "event_name": event["name"],
                        "score": score,
                        "reason": self._generate_recommendation_reason(event, user_prefs),
                        "category": event.get("category", "general"),
                        "date": event.get("date", "")
                    })
            
            # Sort by score and return top limit
            scored_events.sort(key=lambda x: x["score"], reverse=True)
            return scored_events[:limit]
            
        except Exception as e:
            logger.error(f"Error getting event recommendations: {e}")
            return []
    
    async def get_user_recommendations(self, user_id: str, limit: int = 10) -> List[Dict[str, Any]]:
        """Get user recommendations based on social graph and interests."""
        try:
            # Get user's social connections
            user_connections = await self._get_user_connections(user_id)
            
            # Get candidate users
            candidates = await self._get_candidate_users(user_id, user_connections)
            
            # Score users
            scored_users = []
            for user in candidates:
                score = await self._calculate_user_score(user_id, user, user_connections)
                if score > 0.1:
                    scored_users.append({
                        "user_id": user["id"],
                        "username": user["username"],
                        "score": score,
                        "reason": self._generate_user_recommendation_reason(user, user_connections),
                        "mutual_friends": user.get("mutual_friends", 0)
                    })
            
            scored_users.sort(key=lambda x: x["score"], reverse=True)
            return scored_users[:limit]
            
        except Exception as e:
            logger.error(f"Error getting user recommendations: {e}")
            return []
    
    async def get_content_recommendations(self, user_id: str, event_id: Optional[str] = None, limit: int = 10) -> List[Dict[str, Any]]:
        """Get content recommendations (posts, photos, videos)."""
        try:
            # Get user's content preferences
            content_prefs = await self._get_content_preferences(user_id)
            
            # Get candidate content
            candidates = await self._get_candidate_content(user_id, event_id)
            
            # Score content
            scored_content = []
            for content in candidates:
                score = await self._calculate_content_score(content_prefs, content)
                if score > 0.1:
                    scored_content.append({
                        "content_id": content["id"],
                        "content_type": content["type"],
                        "score": score,
                        "reason": self._generate_content_recommendation_reason(content, content_prefs)
                    })
            
            scored_content.sort(key=lambda x: x["score"], reverse=True)
            return scored_content[:limit]
            
        except Exception as e:
            logger.error(f"Error getting content recommendations: {e}")
            return []
    
    async def get_trending_events(self, limit: int = 10) -> List[Dict[str, Any]]:
        """Get currently trending events."""
        try:
            # This would typically query a database for events with high engagement
            trending = []
            
            # Placeholder for trending algorithm
            # In production, this would consider:
            # - Number of RSVPs in last 24h
            # - Social media mentions
            # - Ticket sales velocity
            # - Location relevance
            
            return trending[:limit]
            
        except Exception as e:
            logger.error(f"Error getting trending events: {e}")
            return []
    
    async def get_similar_events(self, event_id: str, limit: int = 5) -> List[Dict[str, Any]]:
        """Get events similar to a given event."""
        try:
            # Get event features
            event_features = await self._get_event_features(event_id)
            
            # Find similar events using cosine similarity
            similar_events = await self._find_similar_events(event_features, limit)
            
            return similar_events
            
        except Exception as e:
            logger.error(f"Error getting similar events: {e}")
            return []
    
    async def _get_user_preferences(self, user_id: str) -> Dict[str, Any]:
        """Get user preferences based on past interactions."""
        # Placeholder - would query database in production
        return {
            "categories": ["music", "tech", "sports"],
            "locations": ["New York", "San Francisco"],
            "price_range": "medium",
            "event_size": "medium"
        }
    
    async def _get_candidate_events(self, user_id: str) -> List[Dict[str, Any]]:
        """Get candidate events to recommend."""
        # Placeholder - would query database in production
        return []
    
    async def _calculate_event_score(self, user_prefs: Dict[str, Any], event: Dict[str, Any]) -> float:
        """Calculate relevance score for an event."""
        score = 0.0
        
        # Category match
        if event.get("category") in user_prefs.get("categories", []):
            score += 0.3
        
        # Location match
        if event.get("location") in user_prefs.get("locations", []):
            score += 0.2
        
        # Popularity score
        popularity = event.get("popularity", 0)
        score += min(popularity / 1000, 0.3)
        
        # Recency score
        event_date = event.get("date")
        if event_date:
            try:
                event_dt = datetime.fromisoformat(event_date.replace('Z', '+00:00'))
                days_until = (event_dt - datetime.now(event_dt.tzinfo)).days
                if 0 <= days_until <= 30:
                    score += 0.2 * (1 - days_until / 30)
            except:
                pass
        
        return min(score, 1.0)
    
    def _generate_recommendation_reason(self, event: Dict[str, Any], user_prefs: Dict[str, Any]) -> str:
        """Generate a human-readable reason for the recommendation."""
        reasons = []
        
        if event.get("category") in user_prefs.get("categories", []):
            reasons.append(f"Matches your interest in {event['category']}")
        
        if event.get("location") in user_prefs.get("locations", []):
            reasons.append(f"Happening in {event['location']}")
        
        popularity = event.get("popularity", 0)
        if popularity > 500:
            reasons.append("Popular event")
        
        return " | ".join(reasons) if reasons else "Recommended for you"
    
    async def _get_user_connections(self, user_id: str) -> Dict[str, Any]:
        """Get user's social connections."""
        return {"followers": [], "following": [], "friends": []}
    
    async def _get_candidate_users(self, user_id: str, connections: Dict[str, Any]) -> List[Dict[str, Any]]:
        """Get candidate users to recommend."""
        return []
    
    async def _calculate_user_score(self, user_id: str, candidate: Dict[str, Any], connections: Dict[str, Any]) -> float:
        """Calculate score for a user recommendation."""
        score = 0.0
        
        # Mutual friends
        mutual = candidate.get("mutual_friends", 0)
        score += min(mutual / 10, 0.4)
        
        # Interest overlap
        common_interests = candidate.get("common_interests", 0)
        score += min(common_interests / 5, 0.3)
        
        return min(score, 1.0)
    
    def _generate_user_recommendation_reason(self, user: Dict[str, Any], connections: Dict[str, Any]) -> str:
        """Generate reason for user recommendation."""
        reasons = []
        
        mutual = user.get("mutual_friends", 0)
        if mutual > 0:
            reasons.append(f"{mutual} mutual friends")
        
        return " | ".join(reasons) if reasons else "People you may know"
    
    async def _get_content_preferences(self, user_id: str) -> Dict[str, Any]:
        """Get user's content preferences."""
        return {"types": ["image", "video"], "topics": []}
    
    async def _get_candidate_content(self, user_id: str, event_id: Optional[str]) -> List[Dict[str, Any]]:
        """Get candidate content."""
        return []
    
    async def _calculate_content_score(self, prefs: Dict[str, Any], content: Dict[str, Any]) -> float:
        """Calculate score for content."""
        return 0.5
    
    def _generate_content_recommendation_reason(self, content: Dict[str, Any], prefs: Dict[str, Any]) -> str:
        """Generate reason for content recommendation."""
        return "Based on your interests"
    
    async def _get_event_features(self, event_id: str) -> Dict[str, Any]:
        """Get features for an event."""
        return {}
    
    async def _find_similar_events(self, features: Dict[str, Any], limit: int) -> List[Dict[str, Any]]:
        """Find similar events using feature similarity."""
        return []
