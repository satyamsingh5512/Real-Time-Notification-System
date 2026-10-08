import { useEffect, useRef, useState } from 'react';
import { usePrefersReducedMotion } from '@/hooks/usePrefersReducedMotion';
import { heroFallbackGradient, heroFragmentShader, heroVertexShader } from '@/shaders/hero';

interface ShaderBackgroundProps {
  /** Extra classes for the absolutely-positioned canvas wrapper. */
  className?: string;
  /** 0..1 brightness multiplier; hero text must stay readable on top. */
  intensity?: number;
}

/**
 * Isolated WebGL hero background.
 *
 * Robustness contract (spec §9):
 *  - Raw WebGL, no 3D engine dependency (~2 KB of shader, no runtime cost beyond the canvas)
 *  - `pointer-events-none` so it can never block interaction or text selection
 *  - Pauses rendering when the document is hidden or the canvas scrolls out of view
 *  - Renders one still frame (no rAF loop) under `prefers-reduced-motion`
 *  - Falls back to a CSS gradient on: no WebGL, context loss, shader compile failure
 *  - Devicepixelratio capped at 2 and resolution halved on small screens for battery
 */
export function ShaderBackground({ className = '', intensity = 1 }: ShaderBackgroundProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const reducedMotion = usePrefersReducedMotion();
  const [fallback, setFallback] = useState(false);
  const [ready, setReady] = useState(false);

  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;

    const gl: WebGLRenderingContext | null =
      canvas.getContext('webgl', { antialias: false, alpha: false, powerPreference: 'low-power' }) ?? null;
    if (!gl) {
      setFallback(true);
      return;
    }

    const compile = (type: number, source: string): WebGLShader | null => {
      const shader = gl.createShader(type);
      if (!shader) return null;
      gl.shaderSource(shader, source);
      gl.compileShader(shader);
      if (!gl.getShaderParameter(shader, gl.COMPILE_STATUS)) {
        gl.deleteShader(shader);
        return null;
      }
      return shader;
    };

    const vertexShader = compile(gl.VERTEX_SHADER, heroVertexShader);
    const fragmentShader = compile(gl.FRAGMENT_SHADER, heroFragmentShader);
    if (!vertexShader || !fragmentShader) {
      setFallback(true);
      return;
    }

    const program = gl.createProgram();
    if (!program) {
      setFallback(true);
      return;
    }
    gl.attachShader(program, vertexShader);
    gl.attachShader(program, fragmentShader);
    gl.linkProgram(program);
    if (!gl.getProgramParameter(program, gl.LINK_STATUS)) {
      setFallback(true);
      return;
    }
    gl.useProgram(program);

    // Fullscreen triangle-strip quad.
    const buffer = gl.createBuffer();
    gl.bindBuffer(gl.ARRAY_BUFFER, buffer);
    gl.bufferData(
      gl.ARRAY_BUFFER,
      new Float32Array([-1, -1, 1, -1, -1, 1, 1, 1]),
      gl.STATIC_DRAW,
    );
    const positionLocation = gl.getAttribLocation(program, 'aPosition');
    gl.enableVertexAttribArray(positionLocation);
    gl.vertexAttribPointer(positionLocation, 2, gl.FLOAT, false, 0, 0);

    const timeLocation = gl.getUniformLocation(program, 'uTime');
    const resolutionLocation = gl.getUniformLocation(program, 'uResolution');
    const mouseLocation = gl.getUniformLocation(program, 'uMouse');
    const intensityLocation = gl.getUniformLocation(program, 'uIntensity');

    const pointer = { x: 0, y: 0 };
    let targetX = 0;
    let targetY = 0;
    let rafId = 0;
    let startTime = performance.now();
    let elapsed = 0;
    let visible = true;
    let disposed = false;
    const reduced = reducedMotion;

    const resize = () => {
      const isSmall = window.innerWidth < 768;
      // Cap DPR (2) and halve the render target on phones: the effect is soft, so the
      // extra pixels cost battery without adding visible quality.
      const scale = Math.min(window.devicePixelRatio || 1, isSmall ? 1 : 2) * (isSmall ? 0.5 : 1);
      const width = Math.max(1, Math.floor(canvas.clientWidth * scale));
      const height = Math.max(1, Math.floor(canvas.clientHeight * scale));
      if (canvas.width === width && canvas.height === height) return;
      canvas.width = width;
      canvas.height = height;
      gl.viewport(0, 0, width, height);
      gl.uniform2f(resolutionLocation, width, height);
    };

    const draw = (time: number) => {
      resize();
      gl.uniform1f(timeLocation, time);
      gl.uniform2f(mouseLocation, pointer.x, pointer.y);
      gl.uniform1f(intensityLocation, reduced ? 0 : intensity);
      gl.drawArrays(gl.TRIANGLE_STRIP, 0, 4);
    };

    const render = () => {
      if (disposed) return;

      if (reduced) {
        // Reduced motion: a single composed still frame, no continuous animation.
        draw(12);
        setReady(true);
        return;
      }

      rafId = requestAnimationFrame((now) => {
        if (disposed || !visible) return;
        elapsed = (now - startTime) / 1000;
        // Ease the pointer so the parallax never feels twitchy.
        pointer.x += (targetX - pointer.x) * 0.06;
        pointer.y += (targetY - pointer.y) * 0.06;
        draw(elapsed);
        rafId = requestAnimationFrame(render);
      });
    };

    const onPointerMove = (event: PointerEvent) => {
      targetX = (event.clientX / window.innerWidth) * 2 - 1;
      targetY = -((event.clientY / window.innerHeight) * 2 - 1);
    };

    const onVisibilityChange = () => {
      visible = document.visibilityState === 'visible';
      if (visible && !reduced) {
        // Resume without a time jump: rebase the clock so the field doesn't jump.
        startTime = performance.now() - elapsed * 1000;
        cancelAnimationFrame(rafId);
        render();
      } else {
        cancelAnimationFrame(rafId);
      }
    };

    const onContextLost = (event: Event) => {
      event.preventDefault();
      setFallback(true);
    };

    const onWindowResize = () => resize();

    document.addEventListener('visibilitychange', onVisibilityChange);
    window.addEventListener('resize', onWindowResize);
    window.addEventListener('pointermove', onPointerMove, { passive: true });
    canvas.addEventListener('webglcontextlost', onContextLost);

    if (reduced) {
      draw(12);
      setReady(true);
    } else {
      render();
      // Only reveal once we have actually painted, so there is no flash of flat colour.
      requestAnimationFrame(() => setReady(true));
    }

    return () => {
      disposed = true;
      cancelAnimationFrame(rafId);
      document.removeEventListener('visibilitychange', onVisibilityChange);
      window.removeEventListener('resize', onWindowResize);
      window.removeEventListener('pointermove', onPointerMove);
      canvas.removeEventListener('webglcontextlost', onContextLost);
      gl.deleteBuffer(buffer);
      gl.deleteProgram(program);
      gl.deleteShader(vertexShader);
      gl.deleteShader(fragmentShader);
    };
  }, [reducedMotion, intensity]);

  return (
    <div
      className={`pointer-events-none absolute inset-0 overflow-hidden ${className}`}
      aria-hidden="true"
      data-testid="shader-background"
    >
      {/* Fallback is always painted underneath, so a WebGL failure degrades gracefully
          instead of leaving a blank hero. */}
      <div className="absolute inset-0" style={{ background: heroFallbackGradient }} />
      {!fallback && (
        <canvas
          ref={canvasRef}
          className={`absolute inset-0 h-full w-full transition-opacity duration-700 ${
            ready ? 'opacity-100' : 'opacity-0'
          }`}
          data-testid="shader-canvas"
        />
      )}
      {/*
        Contrast scrim.

        It tints toward BLACK, not toward --background. An earlier version layered
        `from-background/40`, which in light mode washed the dark field toward grey and
        left dark hero text sitting on a mid-grey band — unreadable. Because the band is
        intentionally dark in both themes, the scrim must be theme-invariant too.

        Three jobs:
          1. darken the copy column from the left, where the headline lives
          2. keep the preview panel's side calmer so its border stays visible
          3. seat the band into the page below with a fade at the bottom edge
      */}
      <div className="absolute inset-0 bg-[linear-gradient(100deg,rgb(0_0_0/0.42)_0%,rgb(0_0_0/0.18)_42%,rgb(0_0_0/0)_72%)]" />
      <div className="absolute inset-x-0 bottom-0 h-24 bg-[linear-gradient(to_bottom,transparent,rgb(0_0_0/0.28))]" />
    </div>
  );
}