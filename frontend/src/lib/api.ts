export type Airport = {
  code: string;
  name: string;
  city: string;
  country: string;
  timezone: string;
};

export type FlightSegment = {
  flightNumber: string;
  airline: string;
  origin: string;
  destination: string;
  departureTime: string;
  arrivalTime: string;
  price: number;
  aircraft: string | null;
};

export type Itinerary = {
  origin: string;
  destination: string;
  segments: FlightSegment[];
  layoverMinutes: number[];
  totalDurationMinutes: number;
  totalPrice: number;
};

export type SearchParams = {
  origin: string;
  destination: string;
  date: string;
};

export class ApiError extends Error {}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080";

async function parseErrorMessage(response: Response): Promise<string> {
  try {
    const body = await response.json();
    if (typeof body?.error === "string") {
      return body.error;
    }
    if (typeof body?.message === "string") {
      return body.message;
    }
  } catch {
    // response body wasn't JSON - fall through to the generic message below
  }
  return `Request failed with status ${response.status}`;
}

export async function fetchAirports(): Promise<Airport[]> {
  const response = await fetch(`${API_BASE_URL}/airports`);
  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response));
  }
  return response.json();
}

export async function searchItineraries(params: SearchParams): Promise<Itinerary[]> {
  const query = new URLSearchParams(params);
  const response = await fetch(`${API_BASE_URL}/itineraries?${query.toString()}`);
  if (!response.ok) {
    throw new ApiError(await parseErrorMessage(response));
  }
  return response.json();
}
