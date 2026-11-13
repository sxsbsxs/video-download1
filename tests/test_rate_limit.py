import unittest

from videodl.rate_limit import RateLimiter


class RateLimiterTests(unittest.TestCase):
    def test_limits_each_client_independently(self):
        limiter = RateLimiter(limit=2, window_seconds=60)
        self.assertTrue(limiter.allow("client-a"))
        self.assertTrue(limiter.allow("client-a"))
        self.assertFalse(limiter.allow("client-a"))
        self.assertTrue(limiter.allow("client-b"))


if __name__ == "__main__":
    unittest.main()
