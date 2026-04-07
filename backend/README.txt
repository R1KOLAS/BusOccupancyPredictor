Backend server for BusOccupancyPredictor

Quick start
1. Open terminal in backend/
2. Create virtual environment:
   python -m venv .venv
3. Activate:
   .venv\Scripts\Activate.ps1
4. Install dependencies:
   pip install -r requirements.txt
5. Run stable server:
   .\start_backend.ps1
6. Run development server with auto-reload:
   .\start_backend_dev.ps1

Available endpoints
- GET /health
- GET /routes
- GET /routes/{route_id}/stops
- POST /predict
- POST /predict/mock

Example health URL
http://127.0.0.1:8000/health

Example docs URL
http://127.0.0.1:8000/docs

Connection notes
- Android emulator should use: http://10.0.2.2:8000/
- Real phone should use your PC local IP, for example: http://192.168.1.10:8000/
- For external devices the backend must run on host 0.0.0.0, which is already configured in the start scripts.
