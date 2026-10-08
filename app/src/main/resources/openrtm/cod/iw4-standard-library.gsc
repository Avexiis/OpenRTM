isHost()
{
	if (self == level)
	{
		return true;
	}
	return self __openrtm_native_ishost();
}

transitionZoomIn(duration)
{
	if (self.elemType == "font" || self.elemType == "timer")
	{
		self.fontScale = 6.3;
		self changeFontScaleOverTime(duration);
		self.fontScale = self.baseFontScale;
	}
	else if (self.elemType == "icon")
	{
		self setShader(self.shader, self.width * 6, self.height * 6);
		self scaleOverTime(duration, self.width, self.height);
	}
}

transitionSlideIn(duration, direction)
{
	if (!isDefined(direction))
	{
		direction = "left";
	}
	if (direction == "left")
	{
		self.x += 1000;
	}
	else if (direction == "right")
	{
		self.x -= 1000;
	}
	else if (direction == "up")
	{
		self.y -= 1000;
	}
	else if (direction == "down")
	{
		self.y += 1000;
	}
	self moveOverTime(duration);
	self.x = self.xOffset;
	self.y = self.yOffset;
}

transitionFadeIn(duration)
{
	self fadeOverTime(duration);
	if (isDefined(self.maxAlpha))
	{
		self.alpha = self.maxAlpha;
	}
	else
	{
		self.alpha = 1;
	}
}
